/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.navigation.keys.CallReadiness
import com.bobbyesp.sona.core.ui.contribution.ReadinessFix
import com.bobbyesp.sona.core.ui.contribution.ReadinessMonitor
import com.bobbyesp.sona.core.ui.contribution.ReadinessResult
import com.bobbyesp.sona.core.ui.contribution.ReadinessState
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.core.ui.contribution.problems
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel

internal data class ReadinessUiState(
    /** `null` until the checks were first answered. */
    val results: List<ReadinessResult>? = null,
) {
    val problems: Int
        get() = results?.problems?.size ?: 0
}

internal sealed interface ReadinessIntent {
    /** The screen is back in front, or a fix was just tried: ask every check again. */
    data object Refresh : ReadinessIntent
}

/** What the system must allow for calls to work, as the app's one [ReadinessMonitor] answers it. */
internal class ReadinessViewModel(private val monitor: ReadinessMonitor) :
    BaseViewModel<ReadinessIntent, ReadinessUiState, Nothing>(ReadinessUiState()) {

    init {
        launch { monitor.results.collect { results -> setState { copy(results = results) } } }
    }

    override fun onHandleIntent(intent: ReadinessIntent) {
        when (intent) {
            ReadinessIntent.Refresh -> monitor.refresh()
        }
    }
}

/** Puts *Call readiness* first in the settings' *Calls* area. */
internal object ReadinessSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Calls

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Link(
                title = R.string.readiness_title,
                icon = Icons.Rounded.Checklist,
                key = CallReadiness(),
                summary = R.string.readiness_settings_summary,
                keywords = R.string.readiness_keywords,
            ),
        )
}

/**
 * The checklist: each thing calls need, whether it is right, and when it is not, what it breaks and
 * the button that fixes it. A fix is carried out here (a permission asked, a screen of the system's
 * settings or of the app opened) and every check is asked again when the screen comes back, so what
 * the user changed elsewhere shows at once.
 *
 * @param firstStart Shown after the first account: it then ends with a way on, not only back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReadinessScreen(
    state: ReadinessUiState,
    firstStart: Boolean,
    onIntent: (ReadinessIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    LifecycleResumeEffect(Unit) {
        onIntent(ReadinessIntent.Refresh)
        onPauseOrDispose {}
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            answers ->
            // Refused for good, Android no longer asks: the switch is in the system's settings.
            val refusedForGood = answers.any { (permission, granted) ->
                !granted && activity?.shouldShowRationale(permission) == false
            }
            if (refusedForGood) context.openSettings(context.appDetails())
            onIntent(ReadinessIntent.Refresh)
        }
    val fix: (ReadinessFix) -> Unit = { what ->
        when (what) {
            is ReadinessFix.AskPermissions -> permissions.launch(what.permissions.toTypedArray())
            is ReadinessFix.SystemSettings -> context.openSettings(what.intent)
            is ReadinessFix.Open -> onOpenKey(what.key)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.readiness_title)) },
                navigationIcon = {
                    if (!firstStart) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.readiness_back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (firstStart) {
                Button(
                    onClick = onBack,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding()
                            .heightIn(min = 56.dp),
                ) {
                    Text(
                        stringResource(
                            if (state.problems == 0) R.string.readiness_done
                            else R.string.readiness_later,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        val results = state.results
        if (results == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
            ) {
                item(key = "summary", contentType = "summary") {
                    Text(
                        text =
                            if (state.problems == 0) stringResource(R.string.readiness_all_good)
                            else
                                pluralStringResource(
                                    R.plurals.readiness_problems,
                                    state.problems,
                                    state.problems,
                                ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
                segmentedItems(results, key = { it.check.id }, contentType = { "check" }) {
                    result,
                    shapes,
                    rowModifier ->
                    CheckRow(result, shapes, rowModifier) { fix(result.check.fix) }
                }
                if (firstStart) {
                    item(key = "reachable", contentType = "note") {
                        Text(
                            stringResource(R.string.readiness_reachable_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    result: ReadinessResult,
    shapes: ListItemShapes,
    modifier: Modifier,
    onFix: () -> Unit,
) {
    val isProblem = result.state == ReadinessState.Problem
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = { if (isProblem) onFix() },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = {
            Icon(
                if (isProblem) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                contentDescription =
                    stringResource(
                        if (isProblem) R.string.readiness_state_problem
                        else R.string.readiness_state_ready,
                    ),
                tint = if (isProblem) colors.error else colors.primary,
            )
        },
        supportingContent =
            if (isProblem) {
                {
                    Column {
                        Text(stringResource(result.check.problem), color = colors.onSurfaceVariant)
                        FilledTonalButton(
                            onClick = onFix,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(result.check.fixLabel))
                        }
                    }
                }
            } else null,
        content = { Text(stringResource(result.check.title), color = colors.onSurface) },
    )
}

private fun Activity.shouldShowRationale(permission: String): Boolean =
    shouldShowRequestPermissionRationale(permission)

private fun Context.appDetails(): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )

/** Opens a screen of the system's settings; where a phone has none for it, the app's own page. */
private fun Context.openSettings(intent: Intent) {
    runCatching { startActivity(intent) }.recoverCatching { startActivity(appDetails()) }
}

@Composable
internal fun ReadinessRoute(
    viewModel: ReadinessViewModel,
    firstStart: Boolean,
    onOpenKey: (NavKey) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReadinessScreen(
        state = state,
        firstStart = firstStart,
        onIntent = viewModel::onSendIntent,
        onOpenKey = onOpenKey,
        onBack = onBack,
    )
}
