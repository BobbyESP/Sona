/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.screenshots

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real demo app, screen by screen, and saves one full-screen PNG of each into the app's
 * external files (`screenshots/NN-name.png`). `scripts/screenshots.sh` sets the language, the theme
 * and the status bar, runs it once per variant and pulls the pictures (docs/releasing.md).
 *
 * Nodes are found by the app's own strings, read by resource name from the language the app runs
 * in, so the same tour serves every language the app has. The screens are Compose, seen through
 * accessibility: a button is its text or its content description, a field is the text of its label.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            "android.permission.ACCESS_LOCAL_NETWORK",
        )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private lateinit var out: File

    @Before
    fun prepare() {
        device.wakeUp()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertTrue("The phone is locked: unlock it and keep it awake", !keyguard.isKeyguardLocked)
        out = requireNotNull(context.getExternalFilesDir(SCREENSHOTS))
        out.deleteRecursively()
        out.mkdirs()
        device.pressHome()
        val launch =
            requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
    }

    @Test
    fun tour() {
        try {
            welcome()
            account()
            contact()
            keypad()
            audioCall()
            failedCall()
            videoCall()
            history()
            contacts()
            messages()
            settings()
            diagnostics()
        } catch (error: Throwable) {
            capture("failure")
            device.dumpWindowHierarchy(File(out, "failure.xml"))
            throw error
        }
    }

    private fun welcome() {
        await(text("onboarding_title"), WELCOME_WITHIN)
        settle()
        capture("01-onboarding")
        click(label("onboarding_add"))
    }

    private fun account() {
        type("field_server", "pbx.sona.example")
        type("field_username", "200")
        type("field_password", "demo-password")
        hideKeyboard()
        settle()
        capture("02-account")
        click(label("check_and_save"))
        await(pattern("checked_ok"), REGISTERS_WITHIN)
        settle()
        capture("03-registered")
        click(label("done"))
        // The checklist a first account leads to; "Listo" is also what its ticks are called.
        await(text("readiness_title"))
        settle()
        click(text("readiness_later"), text("readiness_done"))
    }

    private fun contact() {
        tab("contacts_title")
        clickUntil(label("contact_editor_name")) { clickActionButton() }
        type("contact_editor_name", CONTACT_NAME)
        type("contact_editor_number", CONTACT_NUMBER)
        hideKeyboard()
        click(label("contact_editor_favorite"))
        click(label("contact_editor_save"))
        await(text("contacts_title"))
    }

    private fun keypad() {
        tab("dialer_title")
        dial(CONTACT_NUMBER)
        settle()
        capture("04-keypad")
    }

    private fun audioCall() {
        callWhenReady(label("call_end"))
        awaitAnswered()
        settle(RINGING_TO_TIMER)
        capture("05-call")
        hangUp()
    }

    private fun failedCall() {
        dial(BUSY_NUMBER)
        callWhenReady(pattern("end_busy"))
        settle()
        capture("06-call-failed")
        await(label("dialer_call"), ENDED_LINGERS)
    }

    private fun videoCall() {
        tab("history_title")
        clickUntil(label("call_end"), CALL_OPENS_WITHIN) {
            find(label("call_video_call"))?.click() ?: click(textContaining(CONTACT_NAME))
        }
        awaitAnswered()
        settle(RINGING_TO_TIMER)
        capture("07-video-call")
        hangUp()
    }

    private fun history() {
        tab("history_title")
        await(textContaining(CONTACT_NAME))
        settle()
        capture("08-history")
    }

    private fun contacts() {
        tab("contacts_title")
        await(textContaining(CONTACT_NAME))
        settle()
        capture("09-contacts")
    }

    private fun messages() {
        tab("messages_title")
        clickUntil(label("new_to")) { clickActionButton() }
        type("new_to", CONTACT_NUMBER)
        hideKeyboard()
        clickUntil(label("conversation_placeholder")) { click(pattern("new_write_to")) }
        type("conversation_placeholder", MESSAGE)
        click(label("conversation_send"))
        await(pattern("demo_sona_reply"), REPLIES_WITHIN)
        hideKeyboard()
        settle()
        capture("11-conversation")
        device.pressBack()
        waitUntilGone(listOf(label("conversation_placeholder")), WAIT)
        settle()
        capture("10-messages")
    }

    private fun settings() {
        clickUntil(text("settings_area_about")) { click(label("settings_open")) }
        settle()
        capture("12-settings")
    }

    private fun diagnostics() {
        clickUntil(label("settings_diagnostics_open")) { click(label("settings_area_diagnostics")) }
        clickUntil(text("diagnostics_engine_running")) { click(label("settings_diagnostics_open")) }
        settle()
        capture("13-diagnostics")
    }

    /** Presses *Call* again if the screen was moving, never once the call screen is up. */
    private fun callWhenReady(expected: List<BySelector>) =
        clickUntil(expected, CALL_OPENS_WITHIN) { find(label("dialer_call"))?.click() }

    /** Taps again while the tap does not bring [expected]: the screen may have been moving. */
    private fun clickUntil(
        expected: List<BySelector>,
        within: Long = TAP_LANDS,
        action: () -> Unit,
    ) {
        repeat(TAPS) {
            action()
            if (appears(expected, within)) return
        }
        throw AssertionError("Not on screen in time: $expected")
    }

    private fun appears(selectors: List<BySelector>, timeout: Long): Boolean =
        try {
            poll("$selectors", timeout) { find(selectors) }
            true
        } catch (_: AssertionError) {
            false
        }

    /** Clears what a call left in the keypad, then types [number] on the keys. */
    private fun dial(number: String) {
        await(label("dialer_call"))
        find(label("dialer_delete"))?.longClick()
        number.forEach { digit ->
            // The number is described by its digits too: the key is the lower of the two.
            tapLowest(By.desc(Pattern.compile("^$digit(, .+)?$")))
        }
    }

    private fun awaitAnswered() {
        await(label("call_end"), CALL_STARTS_WITHIN)
        val ringing = listOf("call_calling", "call_ringing").map { text(it) }
        waitUntilGone(ringing, REGISTERS_WITHIN)
    }

    private fun hangUp() {
        // A video call's controls fade away; a tap brings them back.
        if (find(label("call_end")) == null)
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        click(label("call_end"))
    }

    /**
     * The floating action button: accessibility gives its label to no node, so it is the one wide
     * button in the lower half of the screen.
     */
    private fun clickActionButton() {
        tap("the floating action button") {
            device.findObjects(By.clazz("android.widget.Button")).firstOrNull {
                it.visibleBounds.width() > device.displayWidth / 3 &&
                    it.visibleBounds.top > device.displayHeight / 2
            }
        }
    }

    /** The tab's label is the lowest text that says its name: the bar is at the bottom. */
    private fun tab(title: String) = tapLowest(By.text(str(title)))

    /** Taps a field by its label and types into it. */
    private fun type(field: String, value: String) {
        click(label(field))
        poll("a field to type ${str(field)} into") {
            device.findObject(By.clazz("android.widget.EditText").focused(true))?.also {
                it.text = value
            }
        }
    }

    private fun hideKeyboard() {
        if (keyboardShown()) device.pressBack()
        val deadline = System.currentTimeMillis() + WAIT
        while (keyboardShown() && System.currentTimeMillis() < deadline) Thread.sleep(POLL)
    }

    private fun keyboardShown(): Boolean =
        instrumentation.uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }

    private fun capture(name: String) {
        val file = File(out, "$name.png")
        assertTrue("Could not save ${file.path}", device.takeScreenshot(file))
    }

    /** A short pause for animations to land: the one place the tour waits for time itself. */
    private fun settle(millis: Long = SETTLE) = Thread.sleep(millis)

    private fun str(name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        require(id != 0) { "No string resource named $name" }
        return context.resources.getString(id)
    }

    private fun text(name: String): List<BySelector> = listOf(By.text(str(name)))

    private fun textContaining(value: String): List<BySelector> = listOf(By.textContains(value))

    /** A button or a row: its text, or its content description when it is an icon. */
    private fun label(name: String): List<BySelector> =
        str(name).let { listOf(By.text(it), By.desc(it)) }

    /** A sentence with arguments in it, as it is on screen whatever they are. */
    private fun pattern(name: String): List<BySelector> {
        val parts = str(name).split(Regex("%\\d\\$[sd]")).joinToString(".+") { Pattern.quote(it) }
        val pattern = Pattern.compile("(?s).*$parts.*")
        return listOf(By.text(pattern), By.desc(pattern))
    }

    private fun find(selectors: List<BySelector>): UiObject2? = selectors.firstNotNullOfOrNull {
        device.findObject(it)
    }

    /**
     * Tries [attempt] until it gives something, for [timeout]. The screen changes under the tour,
     * so a node found a moment ago may be gone: that is another try, not a failure.
     */
    private fun <T : Any> poll(what: String, timeout: Long = WAIT, attempt: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeout
        do {
            try {
                attempt()?.let {
                    return it
                }
            } catch (_: StaleObjectException) {}
            Thread.sleep(POLL)
        } while (System.currentTimeMillis() < deadline)
        throw AssertionError("Not on screen in time: $what")
    }

    private fun await(selectors: List<BySelector>, timeout: Long = WAIT) =
        poll("$selectors", timeout) { find(selectors) }

    private fun waitUntilGone(selectors: List<List<BySelector>>, timeout: Long) {
        val deadline = System.currentTimeMillis() + timeout
        while (selectors.any { find(it) != null }) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Still on screen: $selectors")
            }
            Thread.sleep(POLL)
        }
    }

    /** Taps the first of [alternatives] that is on screen. */
    private fun click(vararg alternatives: List<BySelector>) {
        tap("${alternatives.toList()}") { alternatives.firstNotNullOfOrNull { find(it) } }
    }

    private fun tapLowest(selector: BySelector) {
        tap("$selector") { device.findObjects(selector).maxByOrNull { it.visibleBounds.top } }
    }

    private fun tap(what: String, find: () -> UiObject2?) {
        poll(what) { find()?.also { it.click() } }
    }

    private companion object {
        const val SCREENSHOTS = "screenshots"
        const val CONTACT_NAME = "Marta Gil"
        const val CONTACT_NUMBER = "100"
        const val BUSY_NUMBER = "486"
        const val MESSAGE = "Hola, ¿me oyes bien?"

        const val WAIT = 10_000L
        const val POLL = 150L
        const val TAPS = 4
        const val CALL_OPENS_WITHIN = 6_000L
        const val TAP_LANDS = 2_500L
        const val SETTLE = 700L
        const val RINGING_TO_TIMER = 1_800L
        const val WELCOME_WITHIN = 30_000L
        const val REGISTERS_WITHIN = 20_000L
        const val CALL_STARTS_WITHIN = 15_000L
        const val ENDED_LINGERS = 10_000L
        const val REPLIES_WITHIN = 30_000L
    }
}
