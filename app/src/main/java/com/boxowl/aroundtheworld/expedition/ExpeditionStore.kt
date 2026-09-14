package com.boxowl.aroundtheworld.expedition

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One small local snapshot for F01. F02's activity ledger will use its own migration. */
class ExpeditionStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "expedition-v1.json"))
    fun load(): Expedition? {
        val bytes = try { file.readFully() } catch (error: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
            return null
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("schema") == 1) { "Unsupported save format" }
        val days = json.getJSONObject("days")
        val events = json.getJSONArray("unlocked")
        return Expedition(
            startedAt = Instant.parse(json.getString("startedAt")),
            zone = ZoneId.of(json.getString("zone")),
            mode = JourneyMode.valueOf(json.getString("mode")),
            routeVersion = json.getInt("routeVersion"),
            lastReadAt = if (json.isNull("lastReadAt")) null else Instant.parse(json.getString("lastReadAt")),
            dailySteps = days.keys().asSequence().associate { LocalDate.parse(it) to days.getLong(it) },
            unlocked = (0 until events.length()).map { events.getString(it) }.toSet(),
        )
    }
    fun save(expedition: Expedition) {
        val days = JSONObject()
        expedition.dailySteps.forEach { (date, count) -> days.put(date.toString(), count) }
        val json = JSONObject().put("schema", 1)
            .put("startedAt", expedition.startedAt.toString()).put("zone", expedition.zone.id)
            .put("mode", expedition.mode.name).put("routeVersion", expedition.routeVersion)
            .put("lastReadAt", expedition.lastReadAt?.toString() ?: JSONObject.NULL)
            .put("days", days).put("unlocked", JSONArray(expedition.unlocked.sorted()))
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
