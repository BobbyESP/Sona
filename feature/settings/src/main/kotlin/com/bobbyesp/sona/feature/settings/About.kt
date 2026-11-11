/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.common.AppInfo
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.serialization.Serializable

/** The software Sona is made with, each with its license. Only this feature opens it. */
@Serializable data object Licenses : NavKey

/**
 * *About*: which Sona this is, the SIP engine under it, and where its source is. Sona is under the
 * GPLv3, as the Linphone SDK it is built on: whoever has the app is owed its source, and this is
 * where they are told.
 */
internal class AboutContributor(info: AppInfo, engine: SipEngine, context: Context) :
    SettingsContributor {
    override val area = SettingsAreas.About

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Info(title = R.string.about_version, text = { info.version }),
            SettingItem.Info(
                title = R.string.about_engine,
                // Read when shown: the engine knows its version once it has started.
                text = { engine.version },
                keywords = R.string.about_engine_keywords,
            ),
            SettingItem.Action(
                title = R.string.about_source,
                summary = R.string.about_source_summary,
                keywords = R.string.about_source_keywords,
                run = { context.openUrl(info.sourceUrl) },
            ),
            SettingItem.Link(
                title = R.string.about_licenses,
                key = Licenses,
                summary = R.string.about_licenses_summary,
                keywords = R.string.about_licenses_keywords,
            ),
        )
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** A work Sona includes: names of software and of licenses are the same in every language. */
internal data class LicensedWork(val name: String, val license: String, val url: String)

/** What the app is built from, the engine first: its license is why Sona's is the GPLv3. */
internal val LicensedWorks: List<LicensedWork> =
    listOf(
        LicensedWork(
            "Linphone SDK (liblinphone, Belle-SIP, Mediastreamer2, oRTP)",
            "GNU General Public License v3.0",
            "https://gitlab.linphone.org/BC/public/linphone-sdk",
        ),
        LicensedWork(
            "Android Jetpack (Compose, Material 3, Navigation 3, Room, DataStore, Core-Telecom)",
            "Apache License 2.0",
            "https://developer.android.com/jetpack",
        ),
        LicensedWork(
            "Kotlin, kotlinx.coroutines, kotlinx.serialization",
            "Apache License 2.0",
            "https://github.com/JetBrains/kotlin",
        ),
        LicensedWork("Koin", "Apache License 2.0", "https://github.com/InsertKoinIO/koin"),
        LicensedWork("Haze", "Apache License 2.0", "https://github.com/chrisbanes/haze"),
        LicensedWork("MaterialKolor", "MIT License", "https://github.com/jordond/MaterialKolor"),
        LicensedWork(
            "Google Sans Flex, JetBrains Mono",
            "SIL Open Font License 1.1",
            "https://openfontlicense.org",
        ),
    )

/** Sona's own license, then every work it includes; a row opens the work's page. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
            ) {
                item(key = "own", contentType = "own") {
                    Text(
                        stringResource(R.string.about_own_license),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
                segmentedItems(LicensedWorks, key = { it.name }, contentType = { "work" }) {
                    work,
                    shapes,
                    rowModifier ->
                    SegmentedListItem(
                        onClick = { context.openUrl(work.url) },
                        shapes = shapes,
                        modifier = rowModifier,
                        colors = segmentedListColors(),
                        supportingContent = { Text(work.license) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        },
                        content = { Text(work.name) },
                    )
                }
            }
        }
    }
}
