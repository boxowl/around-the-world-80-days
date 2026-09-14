package com.boxowl.aroundtheworld.expedition

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "expedition")
data class ExpeditionRow(
    @PrimaryKey val id: Int = 1,
    val startedAt: String,
    val zone: String,
    val mode: String,
    val lastReadAt: String?,
    val routeVersion: Int,
)

@Entity(tableName = "daily_steps")
data class DailyStepsRow(@PrimaryKey val date: String, val count: Long)

@Entity(tableName = "unlocked_diary")
data class DiaryRow(@PrimaryKey val stopId: String)

@Dao
interface ExpeditionDao {
    @Query("SELECT * FROM expedition WHERE id = 1") suspend fun expedition(): ExpeditionRow?
    @Query("SELECT * FROM daily_steps") suspend fun days(): List<DailyStepsRow>
    @Query("SELECT * FROM unlocked_diary") suspend fun diary(): List<DiaryRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExpedition(row: ExpeditionRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDays(rows: List<DailyStepsRow>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putDiary(rows: List<DiaryRow>)
}

@Database(entities = [ExpeditionRow::class, DailyStepsRow::class, DiaryRow::class], version = 1, exportSchema = false)
abstract class ExpeditionDatabase : RoomDatabase() {
    abstract fun expeditionDao(): ExpeditionDao
    companion object {
        @Volatile private var instance: ExpeditionDatabase? = null
        fun get(context: Context): ExpeditionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ExpeditionDatabase::class.java, "expedition.db")
                .build().also { instance = it }
        }
    }
}

/** The F01 AtomicFile is retained as an untouched recovery copy after an atomic Room import. */
class ExpeditionRepository(
    private val database: ExpeditionDatabase,
    private val legacy: ExpeditionStore,
) : ExpeditionLedger {
    private val dao get() = database.expeditionDao()

    suspend fun load(): Expedition? {
        // A Room row is authoritative. A corrupt Room database must not be hidden by a stale F01 file.
        if (dao.expedition() == null) {
            val old = legacy.load() ?: return null
            database.withTransaction {
                if (dao.expedition() == null) write(old)
            }
        }
        return database.withTransaction { read() }
    }

    suspend fun start(mode: JourneyMode, now: Instant, zone: ZoneId): Expedition = database.withTransaction {
        // load() must run first, including legacy import. Recheck to prevent a stale UI starting twice.
        read() ?: Expedition(now, zone, mode).also { write(it) }
    }

    override suspend fun reconcile(replacements: Map<LocalDate, Long>, readAt: Instant): Expedition = database.withTransaction {
        val current = read() ?: error("Expedition is missing")
        val revised = current.reconcile(replacements, readAt)
        write(revised)
        revised
    }

    private suspend fun read(): Expedition? {
        val row = dao.expedition() ?: return null
        return Expedition(
            startedAt = Instant.parse(row.startedAt), zone = ZoneId.of(row.zone),
            mode = JourneyMode.valueOf(row.mode), routeVersion = row.routeVersion,
            lastReadAt = row.lastReadAt?.let(Instant::parse),
            dailySteps = dao.days().associate { LocalDate.parse(it.date) to it.count },
            unlocked = dao.diary().map { it.stopId }.toSet(),
        )
    }

    private suspend fun write(expedition: Expedition) {
        dao.putExpedition(ExpeditionRow(
            startedAt = expedition.startedAt.toString(), zone = expedition.zone.id,
            mode = expedition.mode.name, lastReadAt = expedition.lastReadAt?.toString(),
            routeVersion = expedition.routeVersion,
        ))
        dao.putDays(expedition.dailySteps.map { DailyStepsRow(it.key.toString(), it.value) })
        dao.putDiary(expedition.unlocked.map(::DiaryRow))
    }
}
