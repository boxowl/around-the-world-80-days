package com.boxowl.aroundtheworld.expedition

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpeditionSchemaV2Test {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, ExpeditionDatabase::class.java, name)
        .addMigrations(ExpeditionDatabase.MIGRATION_1_2).build()

    @Test fun migratesActualV1FileWithoutMovingProgressOrDiary() = runBlocking {
        val name = "f03-v1-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            old.execSQL("CREATE TABLE expedition (id INTEGER NOT NULL PRIMARY KEY, startedAt TEXT NOT NULL, zone TEXT NOT NULL, mode TEXT NOT NULL, lastReadAt TEXT, routeVersion INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE daily_steps (date TEXT NOT NULL PRIMARY KEY, count INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE unlocked_diary (stopId TEXT NOT NULL PRIMARY KEY)")
            old.execSQL("INSERT INTO expedition VALUES (1, '2026-09-13T07:00:00Z', 'Europe/Moscow', 'WAGER', '2026-09-13T07:02:00Z', 1)")
            old.execSQL("INSERT INTO daily_steps VALUES ('2026-09-13', 4200)")
            old.execSQL("INSERT INTO unlocked_diary VALUES ('london')")
            old.execSQL("INSERT INTO unlocked_diary VALUES ('dover')")
            old.version = 1
        } finally { old.close() }
        try {
            val first = open(name)
            val loaded = try { ExpeditionRepository(first, ExpeditionStore(context)).load()!! } finally { first.close() }
            assertEquals(BASE_PACE, loaded.paceStepsPerDay)
            assertEquals(WORLD_GOAL, loaded.worldGoal)
            assertEquals(FIRST_LEG_GOAL, loaded.firstLegGoal)
            assertEquals(4_200L, loaded.totalSteps)
            assertEquals(setOf("london", "dover"), loaded.unlocked)
            assertEquals(Instant.parse("2026-09-13T07:02:00Z"), loaded.lastReadAt)
            val reopened = open(name)
            try { assertEquals(loaded, ExpeditionRepository(reopened, ExpeditionStore(context)).load()) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun chosenTargetAndDiarySurviveRestartAndSecondStartCannotReplaceThem() = runBlocking {
        val name = "f03-start-${System.nanoTime()}.db"
        val folder = java.io.File(context.cacheDir, "f03-legacy-${System.nanoTime()}").also { it.mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = folder }
        val start = Instant.parse("2026-09-13T07:00:00Z")
        val zone = ZoneId.of("Europe/Moscow")
        try {
            val firstDb = open(name)
            val first = try {
                val repo = ExpeditionRepository(firstDb, ExpeditionStore(isolated))
                assertNull(repo.load())
                val begun = repo.start(JourneyMode.FREE, start, zone, 10_000)
                assertEquals(begun, repo.start(JourneyMode.WAGER, start.plusSeconds(20), zone, 5_000))
                repo.reconcile(mapOf(LocalDate.parse("2026-09-13") to 5_800L), start.plusSeconds(100))
            } finally { firstDb.close() }
            val secondDb = open(name)
            try {
                val restored = ExpeditionRepository(secondDb, ExpeditionStore(isolated)).load()!!
                assertEquals(first, restored)
                assertEquals(JourneyMode.FREE, restored.mode)
                assertEquals(10_000, restored.paceStepsPerDay)
                assertEquals(800_000L, restored.worldGoal)
                assertEquals(70_000L, restored.firstLegGoal)
                assertEquals(5_800L, restored.totalSteps)
                assertTrue("dover" in restored.unlocked)
                assertEquals("calais", restored.nextStop?.id)
            } finally { secondDb.close() }
        } finally { context.deleteDatabase(name); folder.deleteRecursively() }
    }
}
