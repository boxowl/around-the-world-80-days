package com.boxowl.aroundtheworld.debug.globe

/**
 * TEST DATA for the V21 globe prototype — hand-drawn approximation of the novel's
 * route with sea legs routed around land. Not a product contract: thresholds,
 * stops and transport semantics belong to docs/ROUTE.md / the future V2 spec.
 *
 * Sea legs carry explicit helper waypoints so no leg cuts across land, and the
 * Pacific crossing keeps helper points around the +-180 antimeridian (all math
 * runs on 3D unit vectors, so the seam itself needs no special casing).
 */
object RouteData {

    enum class Transport { TRAIN, SHIP }

    data class Stop(val name: String, val lat: Double, val lon: Double)

    /** [modeToNext] is the transport of the leg starting at this node; null for the last. */
    data class Node(val lat: Double, val lon: Double, val modeToNext: Transport?, val stop: String? = null)

    private fun n(lat: Double, lon: Double, mode: Transport?, stop: String? = null) = Node(lat, lon, mode, stop)

    val nodes: List<Node> = listOf(
        n(51.5074, -0.1278, Transport.TRAIN, "Лондон"),
        n(51.1295, 1.3089, Transport.SHIP, "Дувр"),
        n(50.9513, 1.8587, Transport.TRAIN, "Кале"),
        n(48.8566, 2.3522, Transport.TRAIN, "Париж"),
        n(45.0703, 7.6869, Transport.TRAIN, "Турин"),
        n(40.6327, 17.9418, Transport.SHIP, "Бриндизи"),
        // Средиземное море: Мессинский пролив, к югу от Греции и Крита, Порт-Саид.
        n(38.25, 15.65, Transport.SHIP),
        n(36.30, 19.50, Transport.SHIP),
        n(34.40, 25.50, Transport.SHIP),
        n(32.20, 31.20, Transport.SHIP),
        n(31.30, 32.30, Transport.SHIP),
        n(29.9668, 32.5498, Transport.SHIP, "Суэц"),
        // Красное море и Баб-эль-Мандеб.
        n(27.50, 34.60, Transport.SHIP),
        n(21.00, 38.80, Transport.SHIP),
        n(15.00, 41.50, Transport.SHIP),
        n(12.65, 43.40, Transport.SHIP),
        n(12.7855, 45.0187, Transport.SHIP, "Аден"),
        // Аравийское море.
        n(12.50, 51.00, Transport.SHIP),
        n(13.00, 58.00, Transport.SHIP),
        n(15.50, 66.00, Transport.SHIP),
        n(18.00, 71.50, Transport.SHIP),
        n(18.9220, 72.8347, Transport.TRAIN, "Бомбей"),
        n(25.4358, 81.8463, Transport.TRAIN, "Аллахабад"),
        n(22.5726, 88.3639, Transport.SHIP, "Калькутта"),
        // Бенгальский залив и Малаккский пролив.
        n(20.00, 89.50, Transport.SHIP),
        n(14.50, 92.50, Transport.SHIP),
        n(8.50, 95.00, Transport.SHIP),
        n(5.00, 97.80, Transport.SHIP),
        n(2.80, 100.60, Transport.SHIP),
        n(1.3521, 103.8198, Transport.SHIP, "Сингапур"),
        // Южно-Китайское море.
        n(6.00, 106.50, Transport.SHIP),
        n(11.00, 109.80, Transport.SHIP),
        n(16.00, 112.00, Transport.SHIP),
        n(20.50, 113.80, Transport.SHIP),
        n(22.3193, 114.1694, Transport.SHIP, "Гонконг"),
        // Вдоль побережья Китая к Шанхаю.
        n(23.80, 117.20, Transport.SHIP),
        n(26.50, 120.30, Transport.SHIP),
        n(29.50, 122.30, Transport.SHIP),
        n(31.10, 122.60, Transport.SHIP, "у Шанхая"),
        // Восточно-Китайское море, к югу от Японии.
        n(31.00, 127.50, Transport.SHIP),
        n(31.50, 131.00, Transport.SHIP),
        n(33.50, 135.50, Transport.SHIP),
        n(35.4437, 139.6380, Transport.SHIP, "Иокогама"),
        // Тихий океан, северная дуга; вспомогательные точки вокруг ±180°.
        n(38.00, 148.00, Transport.SHIP),
        n(40.50, 160.00, Transport.SHIP),
        n(42.00, 172.00, Transport.SHIP),
        n(43.00, -176.00, Transport.SHIP),
        n(42.50, -164.00, Transport.SHIP),
        n(40.00, -150.00, Transport.SHIP),
        n(37.50, -136.00, Transport.SHIP),
        n(37.7749, -122.4194, Transport.TRAIN, "Сан-Франциско"),
        n(41.2565, -95.9345, Transport.TRAIN, "Омаха"),
        n(41.8781, -87.6298, Transport.TRAIN, "Чикаго"),
        n(40.7128, -74.0060, Transport.SHIP, "Нью-Йорк"),
        // Северная Атлантика.
        n(40.50, -65.00, Transport.SHIP),
        n(43.50, -50.00, Transport.SHIP),
        n(47.00, -35.00, Transport.SHIP),
        n(49.50, -20.00, Transport.SHIP),
        n(51.30, -10.50, Transport.SHIP),
        n(51.8500, -8.2943, Transport.SHIP, "Квинстаун"),
        n(53.20, -5.80, Transport.SHIP),
        n(53.4084, -2.9916, Transport.TRAIN, "Ливерпуль"),
        n(52.50, -2.00, Transport.TRAIN),
        n(51.5074, -0.1278, null, "Лондон"),
    )

    val stops: List<Stop> = nodes.mapNotNull { node ->
        node.stop?.let { Stop(it, node.lat, node.lon) }
    }.let { all ->
        // Drop the duplicated final "Лондон" (same place as the start).
        all.subList(0, all.size - 1)
    }

    /** Dense route polyline with cumulative distance (degrees of arc) and leg transport. */
    class Route(
        val points: List<Vec3>,
        val distDeg: DoubleArray,
        val segMode: List<Transport>,
        val totalDeg: Double,
        val heroDistDeg: Double,
        val heroPos: Vec3,
    )

    /** TEST hero position: inside the Port-Said -> Suez leg (color splits mid-leg). */
    private const val HERO_LEG_STOP = "Суэц"
    private const val HERO_LEG_FRACTION = 0.85

    fun build(stepDeg: Double = 1.5): Route {
        val pts = ArrayList<Vec3>()
        val dist = ArrayList<Double>()
        val modes = ArrayList<Transport>()
        var acc = 0.0
        var heroDist = -1.0
        var heroPos: Vec3? = null

        for (i in 0 until nodes.size - 1) {
            val from = nodes[i]
            val to = nodes[i + 1]
            val mode = from.modeToNext ?: continue
            val a = latLonToVec(from.lat, from.lon)
            val b = latLonToVec(to.lat, to.lon)
            val legDeg = angularDistanceDeg(a, b)
            val steps = maxOf(1, kotlin.math.ceil(legDeg / stepDeg).toInt())
            if (to.stop == HERO_LEG_STOP && heroDist < 0.0) {
                heroDist = acc + legDeg * HERO_LEG_FRACTION
                heroPos = slerp(a, b, HERO_LEG_FRACTION)
            }
            if (pts.isEmpty()) {
                pts.add(a)
                dist.add(acc)
            }
            for (s in 1..steps) {
                pts.add(slerp(a, b, s.toDouble() / steps))
                dist.add(acc + legDeg * s / steps)
                modes.add(mode)
            }
            acc += legDeg
        }
        return Route(
            points = pts,
            distDeg = dist.toDoubleArray(),
            segMode = modes,
            totalDeg = acc,
            heroDistDeg = heroDist,
            heroPos = heroPos ?: pts.first(),
        )
    }
}
