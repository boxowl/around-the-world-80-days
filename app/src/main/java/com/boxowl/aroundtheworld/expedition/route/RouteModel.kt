package com.boxowl.aroundtheworld.expedition.route

/**
 * Data model of the versioned route geometry dataset (V2.1b).
 *
 * The dataset is a measurement instrument, not a product contract: it fixes
 * the geometry of the novel's route (nodes, sea-lane detours, transport) so
 * the total length D can be measured reproducibly. The scaling hypothesis
 * k, the target length D and the expedition calendar periods are deliberately
 * NOT encoded here — only [ROUTE_GEOMETRY_VERSION] and the Earth radius are
 * constants. Narrative/documentation strings are in Russian, like the rest
 * of the design docs.
 */

/** Version of the route geometry dataset. Bump on any node or leg change. */
const val ROUTE_GEOMETRY_VERSION = 1

/** Role of a route node. */
enum class NodeRole {
    /** Остановка маршрута по роману (город, где герои выходят из транспорта). */
    STOP,

    /** Порт: посадка/высадка на морское плечо. */
    PORT,

    /** Заход в порт без остановки сюжета (Нагасаки у General Grant). */
    CALL,

    /** Промежуточная точка геометрии (станция, морская обходная точка). */
    WAYPOINT,
}

/** Transport of a leg, по роману. */
enum class Transport { TRAIN, SHIP, FERRY, ELEPHANT, SLEDGE, WATERWAY }

/** Provenance of a node's coordinates. */
enum class GeometrySource {
    /** Станция/порт исторической линии 1872 г. (координаты современного населённого пункта). */
    STATIONS_1872,

    /** Ручная морская обходная точка (фарватер вне видимой суши). */
    MANUAL_SEA_LANE,

    /** Плечо-хорда, заданная только концами (сани через прерию). */
    CHORD,

    /** Литературная конвенция: вымышленная точка романа (Кхолби). */
    LITERARY_CONVENTION,
}

/**
 * The 10 chapter groups of the draft docs/design/v2-route-table.md.
 * Legs are grouped into these rows for the comparison table.
 */
enum class DraftRow(val label: String, val titleRu: String) {
    III_IV("III–IV", "Лондон → Дувр → Кале → Париж"),
    V("V", "Париж → Турин → Бриндизи"),
    V_VIII("V–VIII", "Бриндизи → Суэц"),
    IX("IX", "Суэц → Аден → Бомбей"),
    XI_XV("XI–XV", "Бомбей → Аллахабад → Калькутта"),
    XVI_XVIII("XVI–XVIII", "Калькутта → Сингапур → Гонконг"),
    XIX_XXIII("XIX–XXIII", "Гонконг → Шанхай → Иокогама"),
    XXIV_XXVI("XXIV–XXVI", "Иокогама → Сан-Франциско"),
    XXVI_XXXI("XXVI–XXXI", "Сан-Франциско → Омаха → Нью-Йорк"),
    XXXII_XXXVI("XXXII–XXXVI", "Нью-Йорк → Квинстаун → Дублин → Ливерпуль → Лондон"),
}

/**
 * A node of the route geometry. [chapter] is the novel chapter (arabic) where
 * the node is passed: for leg endpoints the chapter of arrival, for waypoints
 * the chapter of passage — it may differ from the leg's own [RouteLeg.chapter]
 * (e.g. the mid-Pacific waypoints of leg-16, which starts in ch. XXIV, carry
 * ch. 25).
 */
data class RouteNode(
    val id: String,
    val nameRu: String,
    val lat: Double,
    val lon: Double,
    val role: NodeRole,
    val chapter: Int,
    val source: GeometrySource,
)

/**
 * A leg between two route nodes, optionally shaped by intermediate waypoint
 * nodes ([waypointIds], in travel order). [pathMultiplier] scales the polyline
 * length for legs whose real path is documented as longer than the straight
 * line (слон через лес: ×1.25); it is explicit data with [assumption], never
 * hidden magic. [uncertaintyPct] documents the systematic error band of the
 * polyline metric for this leg (ж/д линии недооцениваются полилинией на ~5–8%).
 */
data class RouteLeg(
    val id: String,
    val fromId: String,
    val toId: String,
    val transport: Transport,
    val chapter: Int,
    val draftRow: DraftRow,
    val waypointIds: List<String> = emptyList(),
    val pathMultiplier: Double = 1.0,
    val assumption: String = "",
    val uncertaintyPct: Int,
)
