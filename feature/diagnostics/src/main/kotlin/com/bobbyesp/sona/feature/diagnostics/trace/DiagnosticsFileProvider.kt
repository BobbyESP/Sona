/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics.trace

import androidx.core.content.FileProvider

/**
 * Lends the files diagnostics shares (the SIP trace) to the app the user sends them to, one grant
 * at a time. A class of its own, so its manifest entry cannot collide with another module's
 * provider.
 */
class DiagnosticsFileProvider : FileProvider()
