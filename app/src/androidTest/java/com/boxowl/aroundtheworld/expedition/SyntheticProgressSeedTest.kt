package com.boxowl.aroundtheworld.expedition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Emulator-only fixture: no Health Connect data is written or claimed to be real. */
@RunWith(AndroidJUnit4::class)
class SyntheticProgressSeedTest {
    @Test fun seedVisibleProgressForManualEmulatorCheck() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Manual emulator-only fixture", arguments.getString("syntheticSeed") == "allow" &&
            (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = ExpeditionRepository(ExpeditionDatabase.get(context), ExpeditionStore(context))
        val existing = repo.load()
        if (existing != null) return@runBlocking
        val start = Instant.now().minusSeconds(3_600)
        val journey = repo.start(JourneyMode.FREE, start, ZoneId.systemDefault())
        val updated = repo.reconcile(mapOf(journey.startDate to 4_500L), Instant.now())
        assertEquals(4_500L, updated.totalSteps)
        assertEquals(updated, repo.load())
    }
}
