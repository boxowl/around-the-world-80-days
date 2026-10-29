package com.boxowl.aroundtheworld.expedition.path

/**
 * Historical transport silhouettes of the "Path" tab (V2.3).
 *
 * Unlike [com.boxowl.aroundtheworld.expedition.route.Transport], which is a
 * coarse metric of the geometry dataset, this enum is a visual contract: ten
 * distinguishable XIX-century silhouettes plus the walking prologue (the
 * heroes themselves move on foot). The row-to-silhouette mapping is fixed in
 * docs/design/v23a-anchor-binding.md; rows whose silhouette is disputed by the
 * owner (class D) keep the assigned value until the owner picks a variant —
 * no 11th category is introduced automatically.
 */
enum class HistoricalTransport(val labelRu: String) {
    /** Пеший пролог: движутся сами персонажи, транспорта нет. */
    WALK("пешком"),

    /** Европейский поезд (Лондон–Бриндизи, Квинстаун–Дублин, Ливерпуль–Лондон). */
    EUROPEAN_TRAIN("европейский поезд"),

    /** Паром через Ла-Манш (Дувр–Кале) — единственный «паром» контракта. */
    FERRY("паром"),

    /** Океанский корабль-пакетбот (Mongolia, General Grant). */
    PACKET_STEAMER("корабль-пакетбот"),

    /** Индийский поезд (GIPR/EIR). */
    INDIAN_TRAIN("индийский поезд"),

    /** Слон Киуни (Кольби–Аллахабад). */
    ELEPHANT("слон"),

    /** Пароход (Rangoon, условный силуэт для шхуны Tankadere). */
    STEAMSHIP("пароход"),

    /** Американский поезд (Central/Union Pacific и далее на восток). */
    AMERICAN_TRAIN("американский поезд"),

    /** Парусные сани Маджа через прерию. */
    SLEDGE("сани"),

    /** Почтовый пароход через Ирландское море (Дублин–Ливерпуль). */
    MAIL_STEAMER("почтовый пароход"),

    /** Торговый парусно-паровой корабль (Henrietta, Нью-Йорк–Квинстаун). */
    CARGO_STEAMER("торговый пароход"),
}
