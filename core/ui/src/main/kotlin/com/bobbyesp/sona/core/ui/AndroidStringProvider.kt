/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

import android.content.Context
import com.bobbyesp.sona.core.common.StringProvider

/** Text for ViewModels from the app's resources, in the app's current locale. */
class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
}
