/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bobbyesp.sona.calling.domain.DialOutcome

/**
 * Why nothing was dialed, as a sentence; `null` when the call did start. Shared by everything that
 * can start a call (keypad, Recents, Contacts), so each says it the same way.
 */
@Composable
fun DialOutcome.problemText(): String? =
    when (this) {
        DialOutcome.NoAccount -> stringResource(R.string.dial_no_account)
        is DialOutcome.AccountOff -> stringResource(R.string.dial_account_off, label)
        is DialOutcome.NotRegistered -> stringResource(R.string.dial_not_registered, label)
        is DialOutcome.InvalidAddress -> stringResource(R.string.dial_invalid)
        is DialOutcome.Failed -> stringResource(R.string.dial_failed, detail)
        is DialOutcome.Started -> null
    }
