/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineState
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.LogLevel
import org.linphone.core.Reason

/** The app's Linphone engine. Linphone stays an implementation detail. */
fun Context.createLinphoneEngine(appVersion: String): SipEngine =
    LinphoneSipEngine(applicationContext, appVersion)

/**
 * The SIP engine on liblinphone.
 *
 * **One thread owns the Core.** Linphone's `Core` is not thread safe: it is created, iterated
 * (every [ITERATE_MS]) and called only on the `sona-core` thread, which every public function
 * switches to. Listeners run there too, and what leaves is an immutable value in a `StateFlow`.
 *
 * **Linphone keeps nothing.** The Core is created without a configuration file and without push or
 * any Linphone service; the accounts are whatever [setAccounts] last gave, rebuilt from the app's
 * storage at every start. See docs/calling.md.
 */
internal class LinphoneSipEngine(private val context: Context, private val appVersion: String) :
    SipEngine {

    private val thread = HandlerThread("sona-core").apply { start() }
    private val handler = Handler(thread.looper)
    private val dispatcher = handler.asCoroutineDispatcher("sona-core")

    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val capabilities: Set<Capability> =
        setOf(
            Capability.AudioCalls,
            Capability.Dtmf,
            Capability.Hold,
            Capability.Srtp,
            Capability.Zrtp,
        )

    private val _registrations = MutableStateFlow<Map<AccountId, RegistrationState>>(emptyMap())
    override val registrations: StateFlow<Map<AccountId, RegistrationState>> =
        _registrations.asStateFlow()

    // Only touched on the core thread.
    private var core: Core? = null
    private var desired: List<AccountSpec> = emptyList()
    private val bound = mutableMapOf<AccountId, Bound>()

    private class Bound(val spec: AccountSpec, val account: Account, val auth: AuthInfo)

    private val iterate =
        object : Runnable {
            override fun run() {
                core?.iterate() ?: return
                handler.postDelayed(this, ITERATE_MS)
            }
        }

    override suspend fun start(config: EngineConfig) =
        withContext(dispatcher) {
            if (core != null) return@withContext
            _state.value = EngineState.Starting
            try {
                val factory = Factory.instance()
                factory.setDebugMode(config.verboseLogs, LOG_TAG)
                factory.loggingService.setLogLevel(
                    if (config.verboseLogs) LogLevel.Message else LogLevel.Warning,
                )
                val created = factory.createCore(null, null, context)
                created.setAutoIterateEnabled(false)
                created.setPushNotificationEnabled(false)
                created.setUserAgent(config.userAgent, appVersion)
                // Random local ports: a fixed 5060 collides with anything else on the phone.
                val transports = created.transports
                transports.udpPort = RANDOM_PORT
                transports.tcpPort = RANDOM_PORT
                transports.tlsPort = RANDOM_PORT
                created.setTransports(transports)
                created.start()
                core = created
                handler.post(iterate)
                _state.value = EngineState.Running
                reconcile(created)
            } catch (error: Throwable) {
                _state.value = EngineState.Failed(error.message ?: error.javaClass.simpleName)
            }
        }

    override suspend fun stop() =
        withContext(dispatcher) {
            val current = core ?: return@withContext
            handler.removeCallbacks(iterate)
            bound.values.forEach { current.removeAccount(it.account) }
            bound.clear()
            current.clearAllAuthInfo()
            current.stop()
            core = null
            _registrations.update { all -> all.mapValues { RegistrationState.Unregistered } }
            _state.value = EngineState.Stopped
        }

    override suspend fun apply(config: EngineConfig) =
        withContext(dispatcher) { core?.setUserAgent(config.userAgent, appVersion) ?: Unit }

    override suspend fun setAccounts(accounts: List<AccountSpec>) =
        withContext(dispatcher) {
            desired = accounts
            core?.let(::reconcile) ?: Unit
        }

    override suspend fun refreshRegistrations() =
        withContext(dispatcher) { core?.refreshRegisters() ?: Unit }

    /** Makes the Core's accounts exactly [desired]: removes, replaces and adds. */
    private fun reconcile(core: Core) {
        val wanted = desired.associateBy { it.id }

        // Gone, or changed: a changed account is replaced whole, which re-registers it.
        bound.entries.toList().forEach { (id, current) ->
            if (wanted[id] != current.spec) {
                core.removeAccount(current.account)
                bound.remove(id)
            }
        }
        _registrations.update { all -> all.filterKeys { it in wanted } }

        // Credentials are rebuilt whole. Linphone keeps its own copy of each auth info, so removing
        // ours by object can miss it, and a replaced account would quietly keep the old password.
        core.clearAllAuthInfo()
        bound.values.forEach { core.addAuthInfo(it.auth) }

        for (spec in desired) {
            if (spec.id in bound) continue
            bind(core, spec)
        }
        core.defaultAccount = desired.firstOrNull { it.enabled }?.let { bound[it.id]?.account }
    }

    private fun bind(core: Core, spec: AccountSpec) {
        val factory = Factory.instance()
        val identity =
            factory.createAddress(spec.identity.uri)
                ?: return setRegistration(spec.id, invalid("Not a SIP address: ${spec.identity}"))
        spec.displayName?.let { identity.setDisplayName(it) }
        val server =
            factory.createAddress(spec.registrar.toSipUri())
                ?: return setRegistration(spec.id, invalid("Not a server: ${spec.registrar}"))
        server.setTransport(spec.registrar.transport.toLinphone())

        val params = core.createAccountParams()
        params.setIdentityAddress(identity)
        params.setServerAddress(server)
        params.setRegisterEnabled(spec.enabled)
        params.setExpires(spec.expiry.inWholeSeconds.toInt())
        params.setPublishEnabled(false)
        params.setPushNotificationAllowed(false)
        params.setRemotePushNotificationAllowed(false)
        spec.outboundProxy?.let { proxy ->
            factory.createAddress(proxy.toSipUri())?.let { params.setRoutesAddresses(arrayOf(it)) }
        }

        // Username is the identity's user; the auth username, when different, is Linphone's
        // userid. No realm or domain: Asterisk's realm need not match the account's domain.
        val auth =
            factory.createAuthInfo(
                spec.identity.user.orEmpty(),
                spec.authUsername,
                spec.password.reveal(),
                null,
                null,
                null,
            )
        val account = core.createAccount(params)
        account.addListener(
            object : AccountListenerStub() {
                override fun onRegistrationStateChanged(
                    account: Account,
                    state: org.linphone.core.RegistrationState,
                    message: String,
                ) {
                    val error = account.errorInfo
                    setRegistration(
                        spec.id,
                        registrationOf(
                            state = state,
                            code = error.protocolCode,
                            phrase = error.phrase,
                            sendFailed = error.reason == Reason.IOError,
                            previous = _registrations.value[spec.id],
                            networkReachable = core.isNetworkReachable,
                            registrar = spec.registrar,
                        ),
                    )
                }
            },
        )
        core.addAuthInfo(auth)
        core.addAccount(account)
        bound[spec.id] = Bound(spec, account, auth)
        setRegistration(
            spec.id,
            if (spec.enabled) RegistrationState.Registering else RegistrationState.Unregistered,
        )
    }

    private fun invalid(detail: String) = RegistrationState.Failed(status = null, detail = detail)

    private fun setRegistration(id: AccountId, state: RegistrationState) {
        _registrations.update { it + (id to state) }
    }

    private companion object {
        const val ITERATE_MS = 20L
        const val RANDOM_PORT = -1
        const val LOG_TAG = "SonaSip"
    }
}
