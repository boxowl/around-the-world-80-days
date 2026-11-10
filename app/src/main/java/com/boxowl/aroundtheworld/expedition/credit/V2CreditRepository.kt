package com.boxowl.aroundtheworld.expedition.credit

import androidx.room.withTransaction
import com.boxowl.aroundtheworld.expedition.DailyCreditRow
import com.boxowl.aroundtheworld.expedition.ExpeditionDatabase
import com.boxowl.aroundtheworld.expedition.ExpeditionV2Row
import com.boxowl.aroundtheworld.expedition.JourneyMode
import com.boxowl.aroundtheworld.expedition.PassportMetaRow
import com.boxowl.aroundtheworld.expedition.PassportStampRow
import com.boxowl.aroundtheworld.expedition.passport.PassportSnapshot
import com.boxowl.aroundtheworld.expedition.passport.PassportV1
import com.boxowl.aroundtheworld.expedition.passport.StampId
import com.boxowl.aroundtheworld.expedition.route.ROUTE_GEOMETRY_VERSION
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

data class V2CreditSnapshot(
    val expedition: ExpeditionV2Row,
    val stepsByDay: Map<LocalDate, Long>,
    val ledger: CreditLedger,
    val passport: PassportSnapshot,
)

/** Isolated v2 storage. The caller supplies only fully read, clipped daily step aggregates. */
class V2CreditRepository(private val database: ExpeditionDatabase) {
    private val dao get() = database.v2Dao()

    /** Explicit confirmed new start; old v1 rows and previous v2 attempts remain untouched. */
    suspend fun start(
        expectedActiveSeq: Long?, now: Instant, zone: ZoneId, mode: JourneyMode, strideMillimeters: Long,
    ): V2CreditSnapshot =
        database.withTransaction {
            require(strideMillimeters > 0L)
            val previous = activeRow()
            require(previous?.id == expectedActiveSeq) { "Active v2 expedition changed before start" }
            if (previous != null) {
                require(now >= Instant.parse(previous.startedAt))
                require(previous.lastReadAt == null || now >= Instant.parse(previous.lastReadAt))
                check(dao.endExpedition(previous.id, now.toString()) == 1)
            }
            val seq = dao.insertExpedition(ExpeditionV2Row(
                startedAt = now.toString(), zone = zone.id, mode = mode.name,
                state = "ACTIVE", activeSlot = 1, endedAt = null, lastReadAt = null,
                routeVersion = 2, geometryVersion = ROUTE_GEOMETRY_VERSION,
                strideMillimeters = strideMillimeters,
                bookMetersPerRealMeter = BOOK_METERS_PER_REAL_METER.toInt(),
            ))
            dao.putMeta(PassportMetaRow(seq, 0L))
            dao.putStamps(listOf(PassportStampRow(seq, StampId.LONDON_START.id, now.toString())))
            snapshot(dao.expedition(seq)!!)
        }

    suspend fun loadActive(): V2CreditSnapshot? = database.withTransaction {
        activeRow()?.let { snapshot(it) }
    }

    /** Missing dates are unchanged; explicit zero replaces a day. Failure before this call writes nothing. */
    suspend fun reconcile(expeditionSeq: Long, replacements: Map<LocalDate, Long>, readAt: Instant): V2CreditSnapshot =
        database.withTransaction {
            val row = activeRow() ?: error("Active v2 expedition is missing")
            require(row.id == expeditionSeq) { "Stale v2 expedition" }
            require(readAt >= Instant.parse(row.startedAt))
            require(row.lastReadAt == null || readAt >= Instant.parse(row.lastReadAt)) { "Stale read" }
            val zone = ZoneId.of(row.zone)
            val firstDay = Instant.parse(row.startedAt).atZone(zone).toLocalDate()
            val lastDay = readAt.atZone(zone).toLocalDate()
            require(replacements.keys.all { it >= firstDay && it <= lastDay }) { "Day outside expedition read window" }
            val previous = snapshot(row)
            val revisedDays = replacements.map { (date, steps) ->
                DailyCreditRow(row.id, date.toString(), steps, StepDistance.realMeters(steps, row.strideMillimeters))
            }
            val revisedLedger = previous.ledger.reconcile(revisedDays.associate { LocalDate.parse(it.date) to it.realMeters })
            val totalBookMeters = revisedLedger.totalBookMeters // Fail before any write on overflow.
            val baselineRow = dao.meta(row.id)
            val baseline = baselineRow?.baselineBookMeters ?: totalBookMeters
            val historical = PassportV1.reconcile(previous.passport, minOf(totalBookMeters, baseline), null)
            val revisedPassport = PassportV1.reconcile(historical, totalBookMeters, readAt)
            dao.putDays(revisedDays)
            dao.putStamps(revisedPassport.granted.map { (id, at) -> PassportStampRow(row.id, id.id, at?.toString()) })
            dao.putMeta(PassportMetaRow(row.id, maxOf(baseline, totalBookMeters)))
            check(dao.setLastReadAt(row.id, readAt.toString()) == 1)
            snapshot(dao.expedition(row.id)!!)
        }

    private suspend fun activeRow(): ExpeditionV2Row? {
        val active = dao.active()
        check(active.size <= 1) { "Multiple active v2 expeditions" }
        check(active.all { it.activeSlot == 1 }) { "Invalid active v2 marker" }
        return active.singleOrNull()
    }

    private suspend fun snapshot(row: ExpeditionV2Row): V2CreditSnapshot {
        require(row.routeVersion == 2 && row.geometryVersion == ROUTE_GEOMETRY_VERSION)
        require(row.bookMetersPerRealMeter.toLong() == BOOK_METERS_PER_REAL_METER)
        val days = dao.days(row.id)
        val stepsByDay = Collections.unmodifiableMap(days.associate { LocalDate.parse(it.date) to it.steps })
        val ledger = CreditLedger.of(days.associate { LocalDate.parse(it.date) to it.realMeters })
        val known = StampId.entries.associateBy { it.id }
        val passport = PassportSnapshot.of(dao.stamps(row.id).mapNotNull { stamp ->
            known[stamp.stampId]?.let { it to stamp.grantedAt?.let(Instant::parse) }
        }.toMap())
        return V2CreditSnapshot(row, stepsByDay, ledger, passport)
    }
}
