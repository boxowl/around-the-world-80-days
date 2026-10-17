package com.boxowl.aroundtheworld.expedition.path

import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import com.boxowl.aroundtheworld.expedition.route.polylineLengthKm

/**
 * Visual anchors of the "Path" tab (V2.3a): the owner's 28 segments
 * (docs/design/V23_PATH_ROUTE_OWNER_INPUT.md) bound to the measured geometry
 * of [RouteDatasetV1] per the binding contract
 * (docs/design/v23a-anchor-binding-proposal.md, actual binding table in
 * docs/design/v23a-anchor-binding.md).
 *
 * Hard rules encoded here:
 * - Thresholds are computed from the dataset (cumulative leg lengths, arc
 *   interpolation for in-leg waypoints), NEVER stored as literals and NEVER
 *   taken from the owner's kilometre column.
 * - Class C anchors are explicit gaps: stable ids and a structured [AnchorGap]
 *   with variants/sources for the owner, but no threshold and no invented
 *   coordinates.
 * - Visual anchors unlock nothing: every row carries [PathReward.NONE]; story
 *   stops, diary entries and passport stamps keep their existing rules.
 * - The geometry dataset is not modified; positions derive from
 *   ROUTE_GEOMETRY_VERSION and are recomputed by the same function on a
 *   dataset version change.
 */

/** Binding class of a visual anchor (per the proposal's A/B/C; D is a row flag). */
enum class PathBindingClass {
    /** Anchor is a dataset node at a leg boundary; threshold = cumulative book km. */
    A,

    /** Anchor lies inside a leg (waypoint); threshold = arc-length interpolation. */
    B,

    /** Documented gap: no unambiguous dataset point, no threshold. */
    C,
}

/** Heroes present on a segment, per the owner's approved schedule. */
enum class PathHero { FOGG, PASSEPARTOUT, AOUDA }

/** Visual anchors never grant rewards; the enum pins that contract. */
enum class PathReward { NONE }

/**
 * Structured description of a class C gap: the binding question for the
 * owner, the compact variants on the table, and the sources behind them.
 */
data class AnchorGap(
    val questionRu: String,
    val variantsRu: List<String>,
    val sourcesRu: String,
)

/** How an anchor is positioned on the measured geometry (or why it is not). */
sealed interface AnchorBinding {
    /** Anchor is the first node of the route; threshold is exactly 0. */
    data class RouteStart(val nodeId: String) : AnchorBinding

    /** Anchor is the endpoint node of [throughLegId]; threshold = cumulative after that leg. */
    data class LegEnd(val nodeId: String, val throughLegId: String) : AnchorBinding

    /**
     * Anchor is a waypoint inside [legId]; threshold = cumulative to the leg
     * start + polyline length of the prefix up to [waypointId] (inclusive),
     * times the leg's pathMultiplier.
     */
    data class OnLegWaypoint(val waypointId: String, val legId: String) : AnchorBinding

    /** Class C: documented gap, no threshold, no invented coordinates. */
    data class Unbound(val gap: AnchorGap) : AnchorBinding
}

/** A stable visual anchor of the "Path" tab. */
data class PathAnchor(
    val id: String,
    val labelRu: String,
    val binding: AnchorBinding,
) {
    val bindingClass: PathBindingClass
        get() = when (binding) {
            is AnchorBinding.RouteStart, is AnchorBinding.LegEnd -> PathBindingClass.A
            is AnchorBinding.OnLegWaypoint -> PathBindingClass.B
            is AnchorBinding.Unbound -> PathBindingClass.C
        }
}

/**
 * One owner segment (row of the 28-row table). [bindingClass] is the class of
 * the end anchor, which determines the row's threshold. [silhouetteDisputed]
 * is the proposal's class D flag: the binding stays valid, but the assigned
 * [historicalTransport] awaits the owner's silhouette decision.
 */
data class PathRow(
    val index: Int,
    val startAnchorId: String,
    val endAnchorId: String,
    val ownerLabelRu: String,
    val bindingClass: PathBindingClass,
    val silhouetteDisputed: Boolean,
    val historicalTransport: HistoricalTransport,
    val heroes: Set<PathHero>,
    val reward: PathReward = PathReward.NONE,
)

/**
 * The 28 visual segments of the "Path" tab and their thresholds in book
 * kilometres, derived from [RouteDatasetV1].
 */
object PathRouteV1 {

    private const val LEG_11_ALLAHABAD_CALCUTTA = "leg-11-allahabad-calcutta"
    private const val LEG_20_OMAHA_NEW_YORK = "leg-20-omaha-new-york"

    private val prologueGap = AnchorGap(
        questionRu = "Куда привязать пеший пролог (дом №7 на Севиль-роу → Реформ-клуб → вокзал)?",
        variantsRu = listOf(
            "Пролог только как сцена до узла london, без геометрических порогов",
            "Включить точки в набор версии 2 с координатами из источника (требует версии набора и ревью)",
        ),
        sourcesRu = "Адреса исторически известны: Севиль-роу, 7; Реформ-клуб на Пэлл-Мэлл; вокзал — старт leg-01",
    )

    private val nevadaGap = AnchorGap(
        questionRu = "Что такое «Невада» владельца — какая станция?",
        variantsRu = listOf(
            "reno (waypoint плеча leg-17-san-francisco-ogden)",
            "winnemucca (waypoint плеча leg-17-san-francisco-ogden)",
        ),
        sourcesRu = "График романа (главы XXVI–XXXI); историческая линия Central Pacific 1869 г.",
    )

    private val utahGap = AnchorGap(
        questionRu = "Что такое «Юта» владельца — какая точка?",
        variantsRu = listOf(
            "ogden (узел набора; экскурсия в Солт-Лейк-Сити в геометрию не входит)",
        ),
        sourcesRu = "График романа (главы XXVI–XXXI); историческая линия Central/Union Pacific 1869 г.",
    )

    private val skiCumberlandGap = AnchorGap(
        questionRu = "«Ски-Камберленд» не идентифицирован в романе и на линии 1869 г. — куда привязать?",
        variantsRu = listOf(
            "Привязать к fort-kearney с сохранением отображаемого названия владельца",
            "Оставить якорь без геометрического порога до уточнения",
        ),
        sourcesRu = "Функционально — начало саней (романный Форт-Керни, fort-kearney есть в наборе)",
    )

    private val jerseyGap = AnchorGap(
        questionRu = "«Джерси» владельца против Нью-Йорка романа и штампа?",
        variantsRu = listOf(
            "Якорь = new-york, «Джерси» — визуальная деталь сцены",
            "Добавить waypoint Джерси-Сити в версию 2 набора (требует версии набора и ревью)",
        ),
        sourcesRu = "Исторический конец трансконтинентального поезда — Джерси-Сити напротив Манхэттена; роман и штамп — Нью-Йорк",
    )

    private fun anchor(id: String, labelRu: String, binding: AnchorBinding) =
        PathAnchor(id, labelRu, binding)

    private fun legEnd(id: String, labelRu: String, nodeId: String, throughLegId: String) =
        anchor(id, labelRu, AnchorBinding.LegEnd(nodeId, throughLegId))

    /** All visual anchors, in route order. Ids are stable; rows reference them. */
    val anchors: List<PathAnchor> = listOf(
        anchor("savile-row-7", "Дом №7 на Севиль-роу", AnchorBinding.Unbound(prologueGap)),
        anchor("reform-club", "Реформ-клуб", AnchorBinding.Unbound(prologueGap)),
        anchor("london-station", "Лондонский вокзал", AnchorBinding.RouteStart("london")),
        legEnd("dover", "Дувр", "dover", "leg-01-london-dover"),
        legEnd("calais", "Кале", "calais", "leg-02-dover-calais"),
        legEnd("paris", "Париж", "paris", "leg-03-calais-paris"),
        legEnd("turin", "Турин", "turin", "leg-04-paris-turin"),
        legEnd("brindisi", "Бриндизи", "brindisi", "leg-05-turin-brindisi"),
        // Бриндизи → Суэц покрывает leg-06a (море) + leg-06b (Суэцкий канал).
        legEnd("suez", "Суэц", "suez", "leg-06b-port-said-suez"),
        legEnd("aden", "Аден", "aden", "leg-07-suez-aden"),
        legEnd("bombay", "Бомбей", "bombay", "leg-08-aden-bombay"),
        // Написание владельца «Кольби»; в наборе узел kholby («Кхолби»).
        legEnd("kholby", "деревня Кольби", "kholby", "leg-09-bombay-kholby"),
        legEnd("allahabad", "Аллахабад", "allahabad", "leg-10-kholby-allahabad"),
        anchor(
            "benares", "Бенарес",
            AnchorBinding.OnLegWaypoint("benares", LEG_11_ALLAHABAD_CALCUTTA),
        ),
        legEnd("calcutta", "Калькутта", "calcutta", LEG_11_ALLAHABAD_CALCUTTA),
        // Калькутта → Сингапур покрывает leg-12a (река Хугли) + leg-12b (море).
        legEnd("singapore", "Сингапур", "singapore", "leg-12b-hooghly-singapore"),
        legEnd("hong-kong", "Гонконг", "hong-kong", "leg-13-singapore-hong-kong"),
        // «Шанхай» владельца: герои не заходят в город — пересадка в море у устья.
        legEnd(
            "shanghai-mouth", "Шанхай (встреча в море)",
            "shanghai-mouth", "leg-14-hong-kong-shanghai-mouth",
        ),
        legEnd("yokohama", "Иокогама", "yokohama", "leg-15-shanghai-mouth-yokohama"),
        legEnd("san-francisco", "Сан-Франциско", "san-francisco", "leg-16-yokohama-san-francisco"),
        anchor("nevada", "Невада", AnchorBinding.Unbound(nevadaGap)),
        anchor("utah", "Юта", AnchorBinding.Unbound(utahGap)),
        anchor("ski-cumberland", "Ски-Камберленд", AnchorBinding.Unbound(skiCumberlandGap)),
        legEnd("omaha", "Омаха", "omaha", "leg-19-fort-kearney-omaha"),
        anchor(
            "chicago", "Чикаго",
            AnchorBinding.OnLegWaypoint("chicago", LEG_20_OMAHA_NEW_YORK),
        ),
        anchor("jersey", "Джерси", AnchorBinding.Unbound(jerseyGap)),
        legEnd("queenstown", "Квинстаун", "queenstown", "leg-21-new-york-queenstown"),
        legEnd("dublin", "Дублин", "dublin", "leg-22-queenstown-dublin"),
        legEnd("liverpool", "Ливерпуль", "liverpool", "leg-23-dublin-liverpool"),
        legEnd("london", "Лондон", "london", "leg-24-liverpool-london"),
    )

    private val anchorById: Map<String, PathAnchor> = anchors.associateBy { it.id }

    fun anchor(id: String): PathAnchor =
        anchorById[id] ?: throw IllegalArgumentException("unknown path anchor: $id")

    private fun heroesOf(index: Int): Set<PathHero> = buildSet {
        add(PathHero.FOGG)
        // Паспарту появляется с «Лондонского вокзала» (строка 2).
        if (index >= 2) add(PathHero.PASSEPARTOUT)
        // Ауда — с индийского участка после спасения, незадолго до Аллахабада (строка 11).
        if (index >= 11) add(PathHero.AOUDA)
    }

    private fun row(
        index: Int,
        startAnchorId: String,
        endAnchorId: String,
        ownerLabelRu: String,
        historicalTransport: HistoricalTransport,
        silhouetteDisputed: Boolean = false,
    ) = PathRow(
        index = index,
        startAnchorId = startAnchorId,
        endAnchorId = endAnchorId,
        ownerLabelRu = ownerLabelRu,
        bindingClass = anchor(endAnchorId).bindingClass,
        silhouetteDisputed = silhouetteDisputed,
        historicalTransport = historicalTransport,
        heroes = heroesOf(index),
    )

    /** The owner's 28 segments, in route order. Owner labels are kept verbatim. */
    val rows: List<PathRow> = listOf(
        row(1, "savile-row-7", "reform-club", "Дом №7 на Севиль-роу → Реформ-клуб", HistoricalTransport.WALK),
        row(2, "london-station", "dover", "Лондонский вокзал → Дувр", HistoricalTransport.EUROPEAN_TRAIN),
        row(3, "dover", "calais", "Дувр → Кале", HistoricalTransport.FERRY),
        row(4, "calais", "paris", "Кале → Париж", HistoricalTransport.EUROPEAN_TRAIN),
        row(5, "paris", "turin", "Париж → Турин", HistoricalTransport.EUROPEAN_TRAIN),
        row(6, "turin", "brindisi", "Турин → Бриндизи", HistoricalTransport.EUROPEAN_TRAIN),
        row(7, "brindisi", "suez", "Бриндизи → Суэц", HistoricalTransport.PACKET_STEAMER, silhouetteDisputed = true),
        row(8, "suez", "aden", "Суэц → Аден", HistoricalTransport.PACKET_STEAMER, silhouetteDisputed = true),
        row(9, "aden", "bombay", "Аден → Бомбей", HistoricalTransport.PACKET_STEAMER, silhouetteDisputed = true),
        row(10, "bombay", "kholby", "Бомбей → деревня Кольби", HistoricalTransport.INDIAN_TRAIN),
        row(11, "kholby", "allahabad", "деревня Кольби → Аллахабад", HistoricalTransport.ELEPHANT),
        row(12, "allahabad", "benares", "Аллахабад → Бенарес", HistoricalTransport.INDIAN_TRAIN),
        row(13, "benares", "calcutta", "Бенарес → Калькутта", HistoricalTransport.INDIAN_TRAIN),
        row(14, "calcutta", "singapore", "Калькутта → Сингапур", HistoricalTransport.STEAMSHIP),
        row(15, "singapore", "hong-kong", "Сингапур → Гонконг", HistoricalTransport.STEAMSHIP),
        row(16, "hong-kong", "shanghai-mouth", "Гонконг → Шанхай", HistoricalTransport.STEAMSHIP, silhouetteDisputed = true),
        row(17, "shanghai-mouth", "yokohama", "Шанхай → Иокогама", HistoricalTransport.PACKET_STEAMER, silhouetteDisputed = true),
        row(18, "yokohama", "san-francisco", "Иокогама → Сан-Франциско", HistoricalTransport.PACKET_STEAMER, silhouetteDisputed = true),
        row(19, "san-francisco", "nevada", "Сан-Франциско → Невада", HistoricalTransport.AMERICAN_TRAIN),
        row(20, "nevada", "utah", "Невада → Юта", HistoricalTransport.AMERICAN_TRAIN),
        row(21, "utah", "ski-cumberland", "Юта → Ски-Камберленд", HistoricalTransport.AMERICAN_TRAIN),
        row(22, "ski-cumberland", "omaha", "Ски-Камберленд → Омаха", HistoricalTransport.SLEDGE),
        row(23, "omaha", "chicago", "Омаха → Чикаго", HistoricalTransport.AMERICAN_TRAIN),
        row(24, "chicago", "jersey", "Чикаго → Джерси", HistoricalTransport.AMERICAN_TRAIN),
        row(25, "jersey", "queenstown", "Джерси → Квинстаун", HistoricalTransport.CARGO_STEAMER),
        row(26, "queenstown", "dublin", "Квинстаун → Дублин", HistoricalTransport.EUROPEAN_TRAIN),
        row(27, "dublin", "liverpool", "Дублин → Ливерпуль", HistoricalTransport.MAIL_STEAMER),
        row(28, "liverpool", "london", "Ливерпуль → Лондон", HistoricalTransport.EUROPEAN_TRAIN),
    )

    private val rowByIndex: Map<Int, PathRow> = rows.associateBy { it.index }

    fun row(index: Int): PathRow =
        rowByIndex[index] ?: throw IllegalArgumentException("unknown path row: $index")

    /**
     * Threshold of an anchor in book kilometres from the route start, derived
     * from [RouteDatasetV1]; null for class C gaps.
     */
    fun anchorThresholdKm(anchorId: String): Double? {
        val cumulative = RouteMeasure.cumulativeKm()
        val legs = RouteDatasetV1.legs
        return when (val binding = anchor(anchorId).binding) {
            is AnchorBinding.RouteStart -> 0.0
            is AnchorBinding.LegEnd -> {
                val legIndex = legs.indexOfFirst { it.id == binding.throughLegId }
                cumulative[legIndex]
            }
            is AnchorBinding.OnLegWaypoint -> {
                val legIndex = legs.indexOfFirst { it.id == binding.legId }
                val leg = legs[legIndex]
                val polyline = RouteDatasetV1.polylineOf(leg)
                val waypointIndex = polyline.indexOfFirst { it.id == binding.waypointId }
                val prefix = polyline.subList(0, waypointIndex + 1)
                val beforeLeg = if (legIndex == 0) 0.0 else cumulative[legIndex - 1]
                beforeLeg + polylineLengthKm(prefix.map { it.lat to it.lon }) * leg.pathMultiplier
            }
            is AnchorBinding.Unbound -> null
        }
    }

    /** Threshold of a row's start anchor; null when the start is a class C gap. */
    fun startThresholdKm(rowIndex: Int): Double? = anchorThresholdKm(row(rowIndex).startAnchorId)

    /** Threshold of a row's end anchor; null for class C rows. */
    fun endThresholdKm(rowIndex: Int): Double? = anchorThresholdKm(row(rowIndex).endAnchorId)

    init {
        require(anchors.size == anchorById.size) { "duplicate path anchor ids" }
        val nodes = RouteDatasetV1.nodes.map { it.id }.toSet()
        val legs = RouteDatasetV1.legs
        for (a in anchors) {
            when (val b = a.binding) {
                is AnchorBinding.RouteStart ->
                    require(b.nodeId == legs.first().fromId) {
                        "anchor ${a.id}: ${b.nodeId} is not the route start"
                    }
                is AnchorBinding.LegEnd -> {
                    require(b.nodeId in nodes) { "anchor ${a.id}: unknown node ${b.nodeId}" }
                    val leg = legs.find { it.id == b.throughLegId }
                        ?: throw IllegalArgumentException("anchor ${a.id}: unknown leg ${b.throughLegId}")
                    require(leg.toId == b.nodeId) {
                        "anchor ${a.id}: ${b.nodeId} is not the endpoint of ${b.throughLegId}"
                    }
                }
                is AnchorBinding.OnLegWaypoint -> {
                    val leg = legs.find { it.id == b.legId }
                        ?: throw IllegalArgumentException("anchor ${a.id}: unknown leg ${b.legId}")
                    require(b.waypointId in leg.waypointIds) {
                        "anchor ${a.id}: ${b.waypointId} is not a waypoint of ${b.legId}"
                    }
                }
                is AnchorBinding.Unbound ->
                    require(b.gap.variantsRu.isNotEmpty()) { "anchor ${a.id}: gap without variants" }
            }
        }
        require(rows.size == 28) { "expected 28 path rows, got ${rows.size}" }
        rows.forEachIndexed { i, r ->
            require(r.index == i + 1) { "path rows out of order at ${r.index}" }
            anchor(r.startAnchorId)
            anchor(r.endAnchorId)
            require(r.bindingClass == anchor(r.endAnchorId).bindingClass) {
                "row ${r.index}: bindingClass disagrees with its end anchor"
            }
            require(r.reward == PathReward.NONE) { "row ${r.index}: visual anchors grant no reward" }
        }
        var previous = 0.0
        for (r in rows) {
            val threshold = endThresholdKm(r.index) ?: continue
            require(threshold >= previous) {
                "row ${r.index}: end threshold $threshold goes backwards (previous $previous)"
            }
            previous = threshold
        }
    }
}
