package com.boxowl.aroundtheworld.expedition

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpeditionSchemaV4Test {
    @Test fun v3UpgradeKeepsLegacyRowsAndCreatesSeparateV2Tables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "v2-schema-v4-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            old.execSQL("CREATE TABLE expedition (id INTEGER NOT NULL PRIMARY KEY, startedAt TEXT NOT NULL, zone TEXT NOT NULL, mode TEXT NOT NULL, lastReadAt TEXT, routeVersion INTEGER NOT NULL, paceStepsPerDay INTEGER NOT NULL, worldGoal INTEGER NOT NULL, firstLegGoal INTEGER NOT NULL, goalExplanation TEXT NOT NULL)")
            old.execSQL("CREATE TABLE daily_steps (date TEXT NOT NULL PRIMARY KEY, count INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE unlocked_diary (stopId TEXT NOT NULL PRIMARY KEY, viewed INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("INSERT INTO expedition VALUES (1, '2026-09-13T07:00:00Z', 'Europe/Moscow', 'FREE', '2026-09-13T07:02:00Z', 1, 7000, 560000, 49000, 'legacy')")
            old.execSQL("INSERT INTO daily_steps VALUES ('2026-09-13', 4200)")
            old.execSQL("INSERT INTO unlocked_diary VALUES ('dover', 0)")
            old.version = 3
        } finally { old.close() }

        try {
            val db = Room.databaseBuilder(context, ExpeditionDatabase::class.java, name)
                .addMigrations(ExpeditionDatabase.MIGRATION_1_2, ExpeditionDatabase.MIGRATION_2_3, ExpeditionDatabase.MIGRATION_3_4)
                .build()
            try {
                assertEquals(4_200L, db.expeditionDao().days().single().count)
                assertEquals(listOf("dover"), db.expeditionDao().unviewedDiary())
                assertEquals("legacy", db.expeditionDao().expedition()!!.goalExplanation)
                assertTrue(db.v2Dao().active().isEmpty())
                assertTrue(db.v2Dao().days(1L).isEmpty())
                assertTrue(db.v2Dao().stamps(1L).isEmpty())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
