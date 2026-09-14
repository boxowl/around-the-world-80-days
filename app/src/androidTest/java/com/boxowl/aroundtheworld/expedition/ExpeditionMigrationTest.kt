package com.boxowl.aroundtheworld.expedition

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpeditionMigrationTest {
    private lateinit var context: Context
    private lateinit var database: ExpeditionDatabase
    private lateinit var legacy: ExpeditionStore
    private lateinit var folder: File
    private val start = Instant.parse("2026-09-13T07:00:00Z")

    @Before fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        folder = File(target.cacheDir, "migration-${System.nanoTime()}").also { it.mkdirs() }
        context = object : ContextWrapper(target) { override fun getFilesDir() = folder }
        legacy = ExpeditionStore(context)
        database = Room.inMemoryDatabaseBuilder(target, ExpeditionDatabase::class.java).build()
    }
    @After fun cleanup() { database.close(); folder.deleteRecursively() }

    @Test fun importsAllF01FieldsAndRoomWinsAfterRestart() = runBlocking {
        val original = Expedition(start, ZoneId.of("Europe/Moscow"), JourneyMode.WAGER)
            .reconcile(mapOf(LocalDate.parse("2026-09-13") to 4_500L), start.plusSeconds(100))
        legacy.save(original)
        val repo = ExpeditionRepository(database, legacy)
        assertEquals(original, repo.load())
        assertTrue(File(folder, "expedition-v1.json").exists())
        val updated = repo.reconcile(mapOf(LocalDate.parse("2026-09-13") to 1_200L), start.plusSeconds(200))
        assertEquals(1_200L, updated.totalSteps)
        assertTrue("dover" in updated.unlocked)
        assertEquals(updated, ExpeditionRepository(database, legacy).load())
        assertEquals(original, legacy.load())
    }

    @Test fun failedRoomTransactionLeavesAtomicFileAndNoPartialRows() = runBlocking {
        val original = Expedition(start, ZoneId.of("Europe/Moscow"), JourneyMode.FREE)
            .reconcile(mapOf(LocalDate.parse("2026-09-13") to 2_000L), start.plusSeconds(100))
        legacy.save(original)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON daily_steps BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertThrows(Exception::class.java) { runBlocking { ExpeditionRepository(database, legacy).load() } }
        assertNull(database.expeditionDao().expedition())
        assertEquals(original, legacy.load())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_import")
        assertEquals(original, ExpeditionRepository(database, legacy).load())
    }

    @Test fun corruptLegacyNeverCreatesFreshJourney() = runBlocking {
        File(folder, "expedition-v1.json").writeText("{broken")
        assertThrows(Exception::class.java) { runBlocking { ExpeditionRepository(database, legacy).load() } }
        assertNull(database.expeditionDao().expedition())
        assertEquals("{broken", File(folder, "expedition-v1.json").readText())
    }
}
