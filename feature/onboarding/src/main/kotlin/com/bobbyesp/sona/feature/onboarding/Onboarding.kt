/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.settings.AppSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.AccountEditor
import com.bobbyesp.sona.core.navigation.keys.CallReadiness
import com.bobbyesp.sona.core.navigation.keys.Onboarding
import com.bobbyesp.sona.core.navigation.motion.RisingMotion
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal sealed interface OnboardingIntent {
    data object AddAccount : OnboardingIntent

    data object Skip : OnboardingIntent
}

internal sealed interface OnboardingEffect {
    data object OpenEditor : OnboardingEffect

    data object Close : OnboardingEffect
}

/** The welcome. Skipping remembers it was seen; adding an account does once the account exists. */
internal class OnboardingViewModel(private val settings: SettingsStore) :
    BaseViewModel<OnboardingIntent, Unit, OnboardingEffect>(Unit) {
    override fun onHandleIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.AddAccount -> sendEffect(OnboardingEffect.OpenEditor)
            OnboardingIntent.Skip ->
                launch {
                    settings.set(AppSettings.OnboardingDone, true)
                    sendEffect(OnboardingEffect.Close)
                }
        }
    }
}

internal object OnboardingSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Onboarding>(metadata = RisingMotion.metadata()) {
            val viewModel: OnboardingViewModel = koinViewModel()
            viewModel.effects.CollectEventsEffect { effect ->
                when (effect) {
                    OnboardingEffect.OpenEditor -> navigator.goTo(AccountEditor())
                    OnboardingEffect.Close -> navigator.removeDestination(Onboarding)
                }
            }
            OnboardingScreen(onIntent = viewModel::onSendIntent)
        }
        entry<CallReadiness> { key ->
            ReadinessRoute(
                viewModel = koinViewModel(),
                firstStart = key.firstStart,
                onOpenKey = navigator::goTo,
                onBack = navigator::goBack,
            )
        }
    }
}

val onboardingModule = module {
    single { OnboardingSection } bind NavSection::class
    single { ReadinessSettingsContributor } bind SettingsContributor::class
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::ReadinessViewModel)
}

/**
 * The first start: Sona's glyph on a slowly turning Material shape, what Sona is in a sentence,
 * three things worth knowing, and the way to the first extension.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingScreen(onIntent: (OnboardingIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rotation by
        rememberInfiniteTransition(label = "OnboardingShape")
            .animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
                label = "OnboardingShapeRotation",
            )

    Surface(modifier = modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(32.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(168.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.primaryContainer),
                )
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(80.dp),
                )
            }
            Spacer(Modifier.size(32.dp))
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            Spacer(Modifier.size(32.dp))
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Point(Icons.Rounded.Dns, R.string.onboarding_point_extension)
                Point(Icons.Rounded.Lock, R.string.onboarding_point_local)
                Point(Icons.Rounded.Troubleshoot, R.string.onboarding_point_why)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
            Button(
                onClick = { onIntent(OnboardingIntent.AddAccount) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(min = 56.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_add),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = { onIntent(OnboardingIntent.Skip) }) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, text: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@PreviewLightDark
@Composable
private fun OnboardingPreview() {
    SonaTheme { OnboardingScreen(onIntent = {}) }
}
