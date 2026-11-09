/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Sona's one database. Version 1 holds the accounts; history, contacts and per-account overrides
 * arrive as new tables with migrations, never by changing a released schema in place (the schemas
 * are exported to `schemas/` and committed).
 */
@Database(entities = [AccountEntity::class], version = 1, exportSchema = true)
abstract class SonaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}

fun Context.createSonaDatabase(): SonaDatabase =
    Room.databaseBuilder(applicationContext, SonaDatabase::class.java, "sona.db").build()
