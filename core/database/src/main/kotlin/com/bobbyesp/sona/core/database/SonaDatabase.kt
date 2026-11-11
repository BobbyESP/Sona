/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Sona's one database. Version 1 held the accounts; version 2 adds the call history and the local
 * contacts; version 3 adds what each account overrides. A released schema is never changed in
 * place: each change is a new version with a migration, and the schemas are exported to `schemas/`
 * and committed.
 */
@Database(
    entities =
        [
            AccountEntity::class,
            AccountOverrideEntity::class,
            CallRecordEntity::class,
            ContactEntity::class,
            ContactNumberEntity::class,
        ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class SonaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun callRecordDao(): CallRecordDao

    abstract fun contactDao(): ContactDao
}

fun Context.createSonaDatabase(): SonaDatabase =
    Room.databaseBuilder(applicationContext, SonaDatabase::class.java, "sona.db").build()
