package com.boxowl.aroundtheworld.expedition

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
class DiaryEventPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val start = Instant.parse("2026-09-13T07:00:00Z")
    private val date = LocalDate.parse("2026-09-13")
    private fun open(name: String) = Room.databaseBuilder(context, ExpeditionDatabase::class.java, name)
        .addMigrations(ExpeditionDatabase.MIGRATION_1_2, ExpeditionDatabase.MIGRATION_2_3).build()

    @Test fun newEventAppearsOnceAndSurvivesRestartAndCorrection() = runBlocking {
        val name = "f06-events-${System.nanoTime()}.db"
        try {
            val first = open(name)
            try {
                val repo = ExpeditionRepository(first, ExpeditionStore(context))
                assertNull(repo.load())
                repo.start(JourneyMode.FREE, start, ZoneId.of("Europe/Moscow"))
                assertTrue(repo.unviewedEvents().isEmpty())
                repo.reconcile(mapOf(date to 999L), start.plusSeconds(1))
                assertTrue(repo.unviewedEvents().isEmpty())
                repo.reconcile(mapOf(date to 1_000L), start.plusSeconds(2))
                assertEquals(listOf("departure"), repo.unviewedEvents())
                repo.reconcile(mapOf(date to 1_100L), start.plusSeconds(3))
                assertEquals(listOf("departure"), repo.unviewedEvents())
            } finally { first.close() }
            val second = open(name)
            try {
                val repo = ExpeditionRepository(second, ExpeditionStore(context))
                assertEquals(listOf("departure"), repo.unviewedEvents())
                repo.markEventViewed("departure")
                repo.reconcile(mapOf(date to 0L), start.plusSeconds(4))
                repo.reconcile(mapOf(date to 1_100L), start.plusSeconds(5))
                assertTrue("departure" in repo.load()!!.unlocked)
                assertTrue(repo.unviewedEvents().isEmpty())
                repo.reconcile(mapOf(date to 15_000L), start.plusSeconds(6))
                assertEquals(listOf("dover", "calais", "paris"), repo.unviewedEvents())
                repo.reconcile(mapOf(date to 15_000L), start.plusSeconds(7))
                assertEquals(listOf("dover", "calais", "paris"), repo.unviewedEvents())
                repo.markEventViewed("dover")
                repo.markEventViewed("calais")
                repo.markEventViewed("paris")
            } finally { second.close() }
            val third = open(name)
            try { assertTrue(ExpeditionRepository(third, ExpeditionStore(context)).unviewedEvents().isEmpty()) }
            finally { third.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun v2DiaryEventsAreOfferedOnceAfterMigration() = runBlocking {
        val name = "f06-v2-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            old.execSQL("CREATE TABLE expedition (id INTEGER NOT NULL PRIMARY KEY, startedAt TEXT NOT NULL, zone TEXT NOT NULL, mode TEXT NOT NULL, lastReadAt TEXT, routeVersion INTEGER NOT NULL, paceStepsPerDay INTEGER NOT NULL, worldGoal INTEGER NOT NULL, firstLegGoal INTEGER NOT NULL, goalExplanation TEXT NOT NULL)")
            old.execSQL("CREATE TABLE daily_steps (date TEXT NOT NULL PRIMARY KEY, count INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE unlocked_diary (stopId TEXT NOT NULL PRIMARY KEY)")
            old.execSQL("INSERT INTO expedition VALUES (1, '2026-09-13T07:00:00Z', 'Europe/Moscow', 'FREE', '2026-09-13T07:02:00Z', 1, 7000, 560000, 49000, 'Original goal')")
            old.execSQL("INSERT INTO daily_steps VALUES ('2026-09-13', 4200)")
            old.execSQL("INSERT INTO unlocked_diary VALUES ('london')")
            old.execSQL("INSERT INTO unlocked_diary VALUES ('departure')")
            old.execSQL("INSERT INTO unlocked_diary VALUES ('dover')")
            old.version = 2
        } finally { old.close() }
        try {
            val db = open(name)
            try {
                val repo = ExpeditionRepository(db, ExpeditionStore(context))
                val loaded = repo.load()!!
                assertEquals(setOf("london", "departure", "dover"), loaded.unlocked)
                assertEquals(start, loaded.startedAt)
                assertEquals(ZoneId.of("Europe/Moscow"), loaded.zone)
                assertEquals(JourneyMode.FREE, loaded.mode)
                assertEquals(7_000, loaded.paceStepsPerDay)
                assertEquals(560_000L, loaded.worldGoal)
                assertEquals(49_000L, loaded.firstLegGoal)
                assertEquals("Original goal", loaded.goalExplanation)
                assertEquals(mapOf(date to 4_200L), loaded.dailySteps)
                assertEquals(start.plusSeconds(120), loaded.lastReadAt)
                assertEquals(listOf("departure", "dover"), repo.unviewedEvents().sorted())
                repo.markEventViewed("departure")
                repo.markEventViewed("dover")
                repo.reconcile(mapOf(date to 7_100L), start.plusSeconds(121))
                assertEquals(listOf("calais"), repo.unviewedEvents())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
