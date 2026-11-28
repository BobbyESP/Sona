/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import com.bobbyesp.sona.calling.domain.CameraPermission
import com.bobbyesp.sona.calling.domain.NetworkKind
import com.bobbyesp.sona.calling.domain.NetworkMonitor
import com.bobbyesp.sona.calling.domain.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

fun Context.cameraPermission(): CameraPermission {
    val context = applicationContext
    return CameraPermission {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
}

/** Android's default network callback, registered only while someone collects. */
class AndroidNetworkMonitor(context: Context, scope: CoroutineScope) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val status: StateFlow<NetworkStatus> = callbackFlow {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    trySend(capabilities.toStatus())
                }

                override fun onLost(network: Network) {
                    trySend(NetworkStatus.None)
                }
            }
        trySend(current())
        // Refused (too many callbacks in the process): the network known now stays.
        val registered = runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }
            .onFailure { Log.w(TAG, "No network callback", it) }
            .isSuccess
        awaitClose { if (registered) manager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), current())

    private fun current(): NetworkStatus =
        manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }?.toStatus()
            ?: NetworkStatus.None

    private fun NetworkCapabilities.toStatus(): NetworkStatus {
        val metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val kind =
            when {
                hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Wifi
                hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Cellular
                // A VPN or a tether that does not say what is under it: what it costs decides.
                metered -> NetworkKind.Cellular
                else -> NetworkKind.Wifi
            }
        return NetworkStatus(kind, metered)
    }

    private companion object {
        const val TAG = "SonaNetwork"
        const val STOP_AFTER_MS = 5_000L
    }
}
