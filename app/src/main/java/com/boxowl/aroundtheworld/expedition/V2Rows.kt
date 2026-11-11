package com.boxowl.aroundtheworld.expedition

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "expedition_v2", indices = [Index(value = ["activeSlot"], unique = true)])
data class ExpeditionV2Row(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAt: String,
    val zone: String,
    val mode: String,
    val state: String,
    /** 1 for ACTIVE, null for ENDED; unique index prevents two active rows. */
    val activeSlot: Int?,
    val endedAt: String?,
    val lastReadAt: String?,
    val routeVersion: Int,
    val geometryVersion: Int,
    val strideMillimeters: Long,
    val bookMetersPerRealMeter: Int,
)

@Entity(tableName = "daily_credit", primaryKeys = ["expeditionSeq", "date"])
data class DailyCreditRow(
    val expeditionSeq: Long,
    val date: String,
    val steps: Long,
    val realMeters: Long,
)

@Entity(tableName = "passport_stamps", primaryKeys = ["expeditionSeq", "stampId"])
data class PassportStampRow(
    val expeditionSeq: Long,
    val stampId: String,
    val grantedAt: String?,
)

@Entity(tableName = "passport_meta")
data class PassportMetaRow(
    @PrimaryKey val expeditionSeq: Long,
    val baselineBookMeters: Long,
)

@Dao
interface V2Dao {
    @Query("SELECT * FROM expedition_v2 WHERE state = 'ACTIVE'") suspend fun active(): List<ExpeditionV2Row>
    @Query("SELECT * FROM expedition_v2 WHERE id = :seq") suspend fun expedition(seq: Long): ExpeditionV2Row?
    @Query("SELECT * FROM daily_credit WHERE expeditionSeq = :seq") suspend fun days(seq: Long): List<DailyCreditRow>
    @Query("SELECT * FROM passport_stamps WHERE expeditionSeq = :seq") suspend fun stamps(seq: Long): List<PassportStampRow>
    @Query("SELECT * FROM passport_meta WHERE expeditionSeq = :seq") suspend fun meta(seq: Long): PassportMetaRow?

    @Insert suspend fun insertExpedition(row: ExpeditionV2Row): Long
    @Query("UPDATE expedition_v2 SET state = 'ENDED', activeSlot = NULL, endedAt = :endedAt WHERE id = :seq AND state = 'ACTIVE' AND activeSlot = 1")
    suspend fun endExpedition(seq: Long, endedAt: String): Int
    @Query("UPDATE expedition_v2 SET lastReadAt = :readAt WHERE id = :seq AND state = 'ACTIVE'")
    suspend fun setLastReadAt(seq: Long, readAt: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDays(rows: List<DailyCreditRow>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putStamps(rows: List<PassportStampRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeta(row: PassportMetaRow)
}
