package com.boxowl.aroundtheworld.expedition.route

import com.boxowl.aroundtheworld.expedition.route.DraftRow.III_IV
import com.boxowl.aroundtheworld.expedition.route.DraftRow.IX
import com.boxowl.aroundtheworld.expedition.route.DraftRow.V
import com.boxowl.aroundtheworld.expedition.route.DraftRow.V_VIII
import com.boxowl.aroundtheworld.expedition.route.DraftRow.XI_XV
import com.boxowl.aroundtheworld.expedition.route.DraftRow.XVI_XVIII
import com.boxowl.aroundtheworld.expedition.route.DraftRow.XIX_XXIII
import com.boxowl.aroundtheworld.expedition.route.DraftRow.XXIV_XXVI
import com.boxowl.aroundtheworld.expedition.route.DraftRow.XXVI_XXXI
import com.boxowl.aroundtheworld.expedition.route.DraftRow.XXXII_XXXVI
import com.boxowl.aroundtheworld.expedition.route.GeometrySource.CHORD
import com.boxowl.aroundtheworld.expedition.route.GeometrySource.LITERARY_CONVENTION
import com.boxowl.aroundtheworld.expedition.route.GeometrySource.MANUAL_SEA_LANE
import com.boxowl.aroundtheworld.expedition.route.GeometrySource.STATIONS_1872
import com.boxowl.aroundtheworld.expedition.route.NodeRole.CALL
import com.boxowl.aroundtheworld.expedition.route.NodeRole.PORT
import com.boxowl.aroundtheworld.expedition.route.NodeRole.STOP
import com.boxowl.aroundtheworld.expedition.route.NodeRole.WAYPOINT
import com.boxowl.aroundtheworld.expedition.route.Transport.ELEPHANT
import com.boxowl.aroundtheworld.expedition.route.Transport.FERRY
import com.boxowl.aroundtheworld.expedition.route.Transport.SHIP
import com.boxowl.aroundtheworld.expedition.route.Transport.SLEDGE
import com.boxowl.aroundtheworld.expedition.route.Transport.TRAIN
import com.boxowl.aroundtheworld.expedition.route.Transport.WATERWAY

/**
 * Versioned geometry dataset of the novel's route (version 1).
 *
 * 24 literary legs (some split into SHIP + WATERWAY records) built from the
 * terra-research V2.1b literary audit (Gutenberg #103, Towle translation).
 * Sea lanes carry manual detour waypoints validated against Natural Earth
 * ne_50m_land (see RouteSeaValidationTest); rail legs follow the 1872 lines
 * via their historical stations. Dataset coordinate fixes relative to the
 * draft table are listed in docs/design/v2-route-geometry.md.
 */
object RouteDatasetV1 {

    val version: Int = ROUTE_GEOMETRY_VERSION

    private fun stop(id: String, nameRu: String, lat: Double, lon: Double, chapter: Int) =
        RouteNode(id, nameRu, lat, lon, STOP, chapter, STATIONS_1872)

    private fun port(id: String, nameRu: String, lat: Double, lon: Double, chapter: Int) =
        RouteNode(id, nameRu, lat, lon, PORT, chapter, STATIONS_1872)

    private fun station(id: String, nameRu: String, lat: Double, lon: Double, chapter: Int) =
        RouteNode(id, nameRu, lat, lon, WAYPOINT, chapter, STATIONS_1872)

    private fun sea(id: String, nameRu: String, lat: Double, lon: Double, chapter: Int) =
        RouteNode(id, nameRu, lat, lon, WAYPOINT, chapter, MANUAL_SEA_LANE)

    val nodes: List<RouteNode> = listOf(
        // Основные остановки и порты (в порядке маршрута).
        stop("london", "Лондон", 51.5074, -0.1278, 3),
        port("dover", "Дувр", 51.1295, 1.3089, 4),
        port("calais", "Кале", 50.9513, 1.8587, 4),
        stop("paris", "Париж", 48.8566, 2.3522, 4),
        stop("turin", "Турин", 45.0703, 7.6869, 5),
        port("brindisi", "Бриндизи", 40.6327, 17.9418, 5),
        port("port-said", "Порт-Саид", 31.265, 32.302, 6),
        port("suez", "Суэц", 29.9668, 32.5498, 7),
        port("aden", "Аден", 12.7855, 45.0187, 9),
        stop("bombay", "Бомбей", 18.9220, 72.8347, 9),
        RouteNode(
            "kholby", "Кхолби", 25.30, 81.10, STOP, 10, LITERARY_CONVENTION,
        ),
        stop("allahabad", "Аллахабад", 25.4358, 81.8463, 14),
        stop("calcutta", "Калькутта", 22.5726, 88.3639, 15),
        port("singapore", "Сингапур", 1.3521, 103.8198, 16),
        port("hong-kong", "Гонконг", 22.3193, 114.1694, 17),
        port("yokohama", "Иокогама", 35.4437, 139.6380, 23),
        stop("san-francisco", "Сан-Франциско", 37.7749, -122.4194, 25),
        stop("ogden", "Огден", 41.223, -111.974, 27),
        stop("fort-kearney", "Форт-Керни", 40.645, -99.005, 29),
        stop("omaha", "Омаха", 41.2565, -95.9345, 31),
        stop("chicago", "Чикаго", 41.8781, -87.6298, 31),
        stop("new-york", "Нью-Йорк", 40.7128, -74.0060, 32),
        port("queenstown", "Квинстаун", 51.8500, -8.2943, 33),
        stop("dublin", "Дублин", 53.3498, -6.2603, 33),
        port("liverpool", "Ливерпуль", 53.4084, -2.9916, 33),
        RouteNode("nagasaki", "Нагасаки", 32.750, 129.878, CALL, 21, STATIONS_1872),
        // Морская точка пересадки Tankadere → General Grant (в море, не заходя в Шанхай).
        sea("shanghai-mouth", "устье Шанхая (пересадка в море)", 31.10, 122.60, 21),
        // Железнодорожные станции 1872 г.
        station("amiens", "Амьен", 49.894, 2.296, 4),
        station("culoz", "Кюло", 45.85, 5.78, 5),
        station("modane", "Модан", 45.202, 6.674, 5),
        station("bardonecchia", "Бардонеккья", 45.075, 6.700, 5),
        station("susa", "Суза", 45.137, 7.046, 5),
        station("bologna", "Болонья", 44.495, 11.343, 5),
        station("ancona", "Анкона", 43.616, 13.518, 5),
        station("foggia", "Фоджа", 41.462, 15.545, 5),
        station("bhusawal", "Бхусавал", 21.045, 75.788, 10),
        station("itarsi", "Итарси", 22.614, 77.762, 10),
        station("jabalpur", "Джабалпур", 23.165, 79.935, 10),
        station("satna", "Сатна", 24.575, 80.832, 10),
        station("benares", "Бенарес", 25.318, 82.973, 14),
        station("moghalsarai", "Могалсарай", 25.280, 83.120, 14),
        station("gaya", "Гая", 24.780, 85.000, 14),
        station("asansol", "Асансол", 23.685, 86.975, 14),
        station("burdwan", "Бёрдван", 23.240, 87.870, 14),
        station("sacramento", "Сакраменто", 38.582, -121.494, 26),
        station("colfax", "Колфакс", 39.100, -120.953, 26),
        station("truckee", "Траки", 39.328, -120.183, 26),
        station("reno", "Рино", 39.530, -119.814, 26),
        station("winnemucca", "Уиннемакка", 40.973, -117.736, 26),
        station("promontory", "Промонтори", 41.618, -112.547, 26),
        station("evanston", "Эванстон", 41.268, -110.963, 27),
        station("medicine-bow", "Медисин-Бо", 41.895, -106.202, 27),
        station("laramie", "Ларами", 41.311, -105.591, 27),
        station("cheyenne", "Шайенн", 41.140, -104.820, 27),
        station("cleveland", "Кливленд", 41.499, -81.694, 31),
        station("albany", "Олбани", 42.653, -73.757, 31),
        station("mallow", "Маллоу", 52.13, -8.64, 33),
        station("limerick-junction", "Лимерик-Джанкшн", 52.50, -8.20, 33),
        station("west-midlands", "Уэст-Мидлендс (безымянная точка)", 52.50, -2.00, 34),
        // Морские обходные точки (уточнены валидатором RouteSeaValidationTest по ne_50m_land;
        // все сдвиги относительно черновика terra-research перечислены в docs/design/v2-route-geometry.md).
        sea("otranto-strait", "пролив Отранто", 40.30, 18.45, 5),
        sea("south-of-leuca", "к югу от мыса Леука", 39.85, 18.90, 5),
        sea("ionian-calabria", "Ионическое море у Калабрии", 38.45, 17.60, 6),
        sea("strait-of-messina", "южный вход в Мессинский пролив", 37.70, 15.75, 6),
        sea("south-of-spartivento", "к югу от мыса Спартивенто", 37.40, 16.20, 6),
        sea("ionian-sea", "Ионическое море", 36.30, 19.50, 6),
        sea("south-of-crete", "к югу от Крита", 34.40, 25.50, 6),
        sea("off-nile-delta", "к дельте Нила", 32.20, 31.20, 7),
        sea("ismailia", "Исмаилия (Суэцкий канал)", 30.590, 32.265, 8),
        sea("suez-gulf-1", "Суэцкий залив", 29.40, 32.65, 9),
        sea("suez-gulf-2", "Суэцкий залив", 28.90, 33.00, 9),
        sea("suez-gulf-3", "Суэцкий залив", 28.40, 33.35, 9),
        sea("suez-gulf-exit", "Суэцкий залив, выход", 27.90, 33.80, 9),
        sea("gulf-of-suez-exit", "выход из Суэцкого залива", 27.50, 34.60, 9),
        sea("red-sea-north", "Красное море, север", 21.00, 38.80, 9),
        sea("red-sea-south", "Красное море, юг", 15.00, 41.50, 9),
        sea("bab-el-mandeb", "Баб-эль-Мандеб (каналом Перим)", 12.58, 43.42, 9),
        sea("aden-approach", "вдоль побережья Йемена к Адену", 12.60, 44.55, 9),
        sea("gulf-of-aden", "Аденский залив", 12.50, 51.00, 9),
        sea("socotra-north", "к северу от Сокотры", 13.00, 53.50, 9),
        sea("arabian-sea-1", "Аравийское море", 13.00, 58.00, 9),
        sea("arabian-sea-2", "Аравийское море", 15.50, 66.00, 9),
        sea("off-bombay", "подход к Бомбею", 18.00, 71.50, 9),
        sea("hooghly-mouth", "устье Хугли", 21.55, 88.15, 16),
        sea("bay-of-bengal-1", "Бенгальский залив", 20.00, 89.50, 16),
        sea("bay-of-bengal-2", "Бенгальский залив", 14.5, 92.5, 16),
        sea("andaman-sea", "Андаманское море", 12.5, 94.5, 16),
        sea("off-nicobar", "к востоку от Никобар", 10.0, 95.5, 16),
        sea("great-channel", "Грейт-Ченнел", 6.4, 94.6, 16),
        sea("aceh-north", "обход Ачеха (северная оконечность Суматры)", 6.10, 95.40, 16),
        sea("off-north-sumatra", "вдоль северной Суматры", 5.35, 97.50, 16),
        sea("malacca-north", "вход в Малаккский пролив", 4.50, 99.00, 16),
        sea("malacca-strait", "Малаккский пролив", 2.8, 100.60, 16),
        sea("malacca-mid", "Малаккский пролив, середина", 2.20, 101.60, 16),
        sea("malacca-south-1", "Малаккский пролив, юг", 1.75, 102.30, 16),
        sea("malacca-south-2", "Малаккский пролив, юг", 1.45, 102.90, 16),
        sea("singapore-approach", "подход к Сингапуру с запада", 1.25, 103.55, 16),
        sea("batam-east", "к востоку от Батама", 1.15, 104.35, 17),
        sea("scs-south", "юг Южно-Китайского моря", 2.00, 104.95, 17),
        sea("south-china-sea-1", "Южно-Китайское море", 6.00, 106.50, 17),
        sea("south-china-sea-2", "Южно-Китайское море", 11.00, 109.80, 17),
        sea("south-china-sea-3", "Южно-Китайское море", 16.00, 112.00, 17),
        sea("south-china-sea-4", "подход к Гонконгу", 20.50, 113.80, 17),
        sea("off-guangdong-1", "вдоль побережья Гуандуна", 22.55, 115.10, 20),
        sea("off-guangdong-2", "вдоль побережья Гуандуна", 22.90, 116.60, 20),
        sea("taiwan-strait-south", "южный вход в Тайваньский пролив", 23.60, 118.00, 20),
        sea("taiwan-strait", "Тайваньский пролив", 25.5, 120.5, 20),
        sea("off-fujian", "у побережья Фуцзяни", 26.50, 120.30, 20),
        sea("off-zhejiang", "у побережья Чжэцзяна", 28.10, 121.80, 20),
        sea("east-china-sea", "Восточно-Китайское море", 29.50, 122.30, 20),
        sea("ecs-exit", "выход в Филиппинское море", 31.00, 127.50, 21),
        sea("goto-bypass", "обход островов Гото", 32.20, 128.30, 21),
        sea("west-of-kyushu", "к западу от Кюсю (западнее островов Косики)", 31.65, 129.75, 21),
        sea("satsuma-south", "к югу от Сацумы", 30.95, 130.35, 21),
        sea("south-of-kyushu", "к югу от Кюсю", 30.90, 131.30, 21),
        sea("kii-south", "к югу от полуострова Кии", 33.15, 135.90, 22),
        sea("enshu-nada", "Энсю-нада", 34.30, 138.40, 22),
        sea("izu-south", "к югу от полуострова Идзу", 34.60, 139.50, 23),
        sea("uraga-channel", "Урагский фарватер", 35.20, 139.72, 24),
        sea("tokyo-bay-exit", "выход из Токийского залива", 34.90, 139.78, 24),
        sea("off-boso", "к востоку от полуострова Босо", 34.8, 141.0, 24),
        sea("pacific-1", "северная тихоокеанская дуга", 38.0, 148.0, 24),
        sea("pacific-2", "северная тихоокеанская дуга", 40.5, 160.0, 25),
        sea("pacific-3", "северная тихоокеанская дуга", 42.0, 172.0, 25),
        sea("pacific-4", "северная тихоокеанская дуга", 43.0, -176.0, 25),
        sea("pacific-5", "северная тихоокеанская дуга", 42.5, -164.0, 25),
        sea("pacific-6", "северная тихоокеанская дуга", 40.0, -150.0, 25),
        sea("pacific-7", "подход к Сан-Франциско", 37.5, -136.0, 25),
        sea("ny-harbor-1", "выход из нью-йоркской гавани", 40.45, -73.8, 32),
        sea("ny-harbor-2", "к югу от Лонг-Айленда", 40.3, -72.0, 32),
        sea("atlantic-1", "Северная Атлантика", 40.5, -65.0, 32),
        sea("atlantic-2", "Северная Атлантика", 43.5, -50.0, 32),
        sea("atlantic-3", "Северная Атлантика", 47.0, -35.0, 32),
        sea("atlantic-4", "Северная Атлантика", 49.5, -20.0, 33),
        sea("fastnet", "маяк Фастнет", 51.39, -9.60, 33),
        sea("off-cork", "подход к Квинстауну", 51.55, -8.95, 33),
        sea("irish-sea-1", "Ирландское море", 53.45, -5.50, 33),
        sea("irish-sea-2", "к северу от Англси", 53.55, -4.00, 33),
    )

    private val byId: Map<String, RouteNode> = nodes.associateBy { it.id }

    fun node(id: String): RouteNode =
        byId[id] ?: throw IllegalArgumentException("unknown route node: $id")

    private fun leg(
        id: String,
        from: String,
        to: String,
        transport: Transport,
        chapter: Int,
        row: DraftRow,
        waypoints: List<String> = emptyList(),
        pathMultiplier: Double = 1.0,
        assumption: String,
        uncertaintyPct: Int,
    ) = RouteLeg(
        id, from, to, transport, chapter, row, waypoints, pathMultiplier, assumption, uncertaintyPct,
    )

    val legs: List<RouteLeg> = listOf(
        leg(
            "leg-01-london-dover", "london", "dover", TRAIN, 3, III_IV,
            assumption = "хорда; реальная линия SER ~120 км (полилиния узлов недооценивает)",
            uncertaintyPct = 10,
        ),
        leg(
            "leg-02-dover-calais", "dover", "calais", FERRY, 4, III_IV,
            assumption = "прямой фарватер через пролив",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-03-calais-paris", "calais", "paris", TRAIN, 4, III_IV,
            waypoints = listOf("amiens"),
            assumption = "линия через Амьен (Chemin de fer du Nord)",
            uncertaintyPct = 8,
        ),
        leg(
            "leg-04-paris-turin", "paris", "turin", TRAIN, 5, V,
            waypoints = listOf("culoz", "modane", "bardonecchia", "susa"),
            assumption = "линия PLM через тоннель Мон-Сени",
            uncertaintyPct = 8,
        ),
        leg(
            "leg-05-turin-brindisi", "turin", "brindisi", TRAIN, 5, V,
            waypoints = listOf("bologna", "ancona", "foggia"),
            assumption = "Адриатическая линия через Болонью, Анкону, Фоджу",
            uncertaintyPct = 8,
        ),
        leg(
            "leg-06a-brindisi-port-said", "brindisi", "port-said", SHIP, 5, V_VIII,
            waypoints = listOf(
                "otranto-strait", "south-of-leuca", "ionian-calabria", "strait-of-messina",
                "south-of-spartivento", "ionian-sea", "south-of-crete", "off-nile-delta",
            ),
            assumption = "пакетбот Mongolia: вдоль Калабрии, мимо южного входа в Мессинский пролив, к югу от Крита",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-06b-port-said-suez", "port-said", "suez", WATERWAY, 8, V_VIII,
            waypoints = listOf("ismailia"),
            assumption = "Суэцкий канал ниже разрешения карты: отдельное плечо-водный путь",
            uncertaintyPct = 10,
        ),
        leg(
            "leg-07-suez-aden", "suez", "aden", SHIP, 9, IX,
            waypoints = listOf(
                "suez-gulf-1", "suez-gulf-2", "suez-gulf-3", "suez-gulf-exit",
                "gulf-of-suez-exit", "red-sea-north", "red-sea-south", "bab-el-mandeb", "aden-approach",
            ),
            assumption = "Красным морем, Баб-эль-Мандеб каналом Перим",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-08-aden-bombay", "aden", "bombay", SHIP, 9, IX,
            waypoints = listOf("gulf-of-aden", "socotra-north", "arabian-sea-1", "arabian-sea-2", "off-bombay"),
            assumption = "Аравийским морем напрямик, к северу от Сокотры",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-09-bombay-kholby", "bombay", "kholby", TRAIN, 10, XI_XV,
            waypoints = listOf("bhusawal", "itarsi", "jabalpur", "satna"),
            assumption = "линия GIPR; Кхолби — вымышленный конец рельсов ~50 миль до Аллахабада",
            uncertaintyPct = 8,
        ),
        leg(
            "leg-10-kholby-allahabad", "kholby", "allahabad", ELEPHANT, 11, XI_XV,
            pathMultiplier = 1.25,
            assumption = "хорда ×1.25: объезд лесом и деревней Пилладжи на слоне Киуни",
            uncertaintyPct = 25,
        ),
        leg(
            "leg-11-allahabad-calcutta", "allahabad", "calcutta", TRAIN, 14, XI_XV,
            waypoints = listOf("benares", "moghalsarai", "gaya", "asansol", "burdwan"),
            assumption = "линия EIR через Бенарес и Асансол",
            uncertaintyPct = 8,
        ),
        leg(
            "leg-12a-calcutta-hooghly", "calcutta", "hooghly-mouth", WATERWAY, 16, XVI_XVIII,
            assumption = "спуск по реке Хугли к морю (~100 км, как канал)",
            uncertaintyPct = 10,
        ),
        leg(
            "leg-12b-hooghly-singapore", "hooghly-mouth", "singapore", SHIP, 16, XVI_XVIII,
            waypoints = listOf(
                "bay-of-bengal-1", "bay-of-bengal-2", "andaman-sea", "off-nicobar",
                "great-channel", "aceh-north", "off-north-sumatra", "malacca-north",
                "malacca-strait", "malacca-mid", "malacca-south-1", "malacca-south-2",
                "singapore-approach",
            ),
            assumption = "пакетбот Rangoon: к востоку от Андаман, Грейт-Ченнел, обход Ачеха, Малаккский пролив",
            uncertaintyPct = 4,
        ),
        leg(
            "leg-13-singapore-hong-kong", "singapore", "hong-kong", SHIP, 17, XVI_XVIII,
            waypoints = listOf(
                "batam-east", "scs-south", "south-china-sea-1", "south-china-sea-2",
                "south-china-sea-3", "south-china-sea-4",
            ),
            assumption = "Rangoon: Южно-Китайским морем вдоль Вьетнама",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-14-hong-kong-shanghai-mouth", "hong-kong", "shanghai-mouth", SHIP, 20, XIX_XXIII,
            waypoints = listOf(
                "off-guangdong-1", "off-guangdong-2", "taiwan-strait-south", "taiwan-strait",
                "off-fujian", "off-zhejiang", "east-china-sea",
            ),
            assumption = "шхуна Tankadere до устья Шанхая (роман: 800 миль ≈ 1287 км)",
            uncertaintyPct = 4,
        ),
        leg(
            "leg-15-shanghai-mouth-yokohama", "shanghai-mouth", "yokohama", SHIP, 21, XIX_XXIII,
            waypoints = listOf(
                "ecs-exit", "goto-bypass", "nagasaki", "west-of-kyushu", "satsuma-south",
                "south-of-kyushu", "kii-south", "enshu-nada", "izu-south",
            ),
            assumption = "General Grant: пересадка в море, заход в Нагасаки (CALL), к югу от Японии",
            uncertaintyPct = 4,
        ),
        leg(
            "leg-16-yokohama-san-francisco", "yokohama", "san-francisco", SHIP, 24, XXIV_XXVI,
            waypoints = listOf(
                "uraga-channel", "tokyo-bay-exit", "off-boso", "pacific-1", "pacific-2", "pacific-3",
                "pacific-4", "pacific-5", "pacific-6", "pacific-7",
            ),
            assumption = "General Grant: северная тихоокеанская дуга через антимеридиан",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-17-san-francisco-ogden", "san-francisco", "ogden", TRAIN, 26, XXVI_XXXI,
            waypoints = listOf("sacramento", "colfax", "truckee", "reno", "winnemucca", "promontory"),
            assumption = "Central Pacific: Сьерра-Невада, Промонтори; экскурсия в Солт-Лейк-Сити не считается",
            uncertaintyPct = 6,
        ),
        leg(
            "leg-18-ogden-fort-kearney", "ogden", "fort-kearney", TRAIN, 27, XXVI_XXXI,
            waypoints = listOf("evanston", "medicine-bow", "laramie", "cheyenne"),
            assumption = "Union Pacific: мост Медисин-Бо, Шайенн, вдоль Платта",
            uncertaintyPct = 6,
        ),
        leg(
            "leg-19-fort-kearney-omaha", "fort-kearney", "omaha", SLEDGE, 31, XXVI_XXXI,
            assumption = "парусные сани Маджа: хорда (роман: «as the birds fly, at most two hundred miles»)",
            uncertaintyPct = 10,
        ),
        leg(
            "leg-20-omaha-new-york", "omaha", "new-york", TRAIN, 31, XXVI_XXXI,
            waypoints = listOf("chicago", "cleveland", "albany"),
            assumption = "через Чикаго, Кливленд, Олбани",
            uncertaintyPct = 8,
        ),
        leg(
            "leg-21-new-york-queenstown", "new-york", "queenstown", SHIP, 32, XXXII_XXXVI,
            waypoints = listOf(
                "ny-harbor-1", "ny-harbor-2", "atlantic-1", "atlantic-2",
                "atlantic-3", "atlantic-4", "fastnet", "off-cork",
            ),
            assumption = "парусно-паровая Henrietta: выход южнее Лонг-Айленда, у Фастнета, вдоль Западного Корка",
            uncertaintyPct = 3,
        ),
        leg(
            "leg-22-queenstown-dublin", "queenstown", "dublin", TRAIN, 33, XXXII_XXXVI,
            waypoints = listOf("mallow", "limerick-junction"),
            assumption = "почтовый ж/д экспресс Квинстаун → Дублин",
            uncertaintyPct = 6,
        ),
        leg(
            "leg-23-dublin-liverpool", "dublin", "liverpool", SHIP, 33, XXXII_XXXVI,
            waypoints = listOf("irish-sea-1", "irish-sea-2"),
            assumption = "пароход через Ирландское море к северу от Англси",
            uncertaintyPct = 5,
        ),
        leg(
            "leg-24-liverpool-london", "liverpool", "london", TRAIN, 34, XXXII_XXXVI,
            waypoints = listOf("west-midlands"),
            assumption = "спецпоезд; хорда через Уэст-Мидлендс",
            uncertaintyPct = 10,
        ),
    )

    init {
        val ids = HashSet<String>()
        for (node in nodes) {
            require(ids.add(node.id)) { "duplicate node id: ${node.id}" }
        }
        for (leg in legs) {
            require(byId.containsKey(leg.fromId)) { "leg ${leg.id}: unknown from ${leg.fromId}" }
            require(byId.containsKey(leg.toId)) { "leg ${leg.id}: unknown to ${leg.toId}" }
            for (w in leg.waypointIds) {
                require(byId.containsKey(w)) { "leg ${leg.id}: unknown waypoint $w" }
            }
            require(leg.pathMultiplier >= 1.0) { "leg ${leg.id}: pathMultiplier < 1" }
            require(leg.uncertaintyPct in 1..100) { "leg ${leg.id}: bad uncertaintyPct" }
        }
    }

    /** Ordered polyline of a leg: from-node, waypoints, to-node. */
    fun polylineOf(leg: RouteLeg): List<RouteNode> =
        listOf(node(leg.fromId)) + leg.waypointIds.map(::node) + node(leg.toId)
}
