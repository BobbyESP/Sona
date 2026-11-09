/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val callModule = module { viewModelOf(::CallViewModel) }
