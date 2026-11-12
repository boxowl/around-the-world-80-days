package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.passport.PassportSnapshot
import com.boxowl.aroundtheworld.expedition.passport.StampId

/** First chapter-finish notes of v2. Visibility follows persisted stamps of this expedition. */
object V2MapNarrative {
    data class Note(val stamp: StampId, val occurrenceId: String, val text: String)
    data class CardNote(val text: String, val opened: Boolean)

    val notes: List<Note> = listOf(
        Note(StampId.LONDON_START, "london#1", "Паспорт открыт. У вокзала начинается дорога вокруг света."),
        Note(StampId.SUEZ_FINISH, "suez#1", "Средиземное море осталось за кормой. В Суэце появляется первый штамп главы."),
        Note(StampId.BOMBAY_FINISH, "bombay#1", "После Красного моря и остановки в Адене судно приходит в Бомбей."),
        Note(StampId.CALCUTTA_FINISH, "calcutta#1", "Индийская дорога сменила поезд лесным обходом и снова вернулась к рельсам."),
        Note(StampId.HONG_KONG_FINISH, "hong-kong#1", "За Сингапуром морской путь приводит путешественников в гавань Гонконга."),
        Note(StampId.YOKOHAMA_FINISH, "yokohama#1", "После перемен в морском пути и пересадки у китайского побережья путешественники достигают Иокогамы."),
        Note(StampId.SAN_FRANCISCO_FINISH, "san-francisco#1", "Тихий океан остался позади. Берег Калифорнии открывает следующую часть пути."),
        Note(StampId.NEW_YORK_FINISH, "new-york#1", "Железная дорога через Америку закончилась у нью-йоркского порта."),
        Note(StampId.LONDON_FINISH, "london#2", "Кругосветная линия замкнулась в Лондоне. В паспорте собраны отметки всего пути."),
    )

    fun forCard(card: GlobeStopCard, passport: PassportSnapshot): CardNote? =
        notes.firstOrNull { it.occurrenceId == card.occurrenceId }?.let { note ->
            CardNote(note.text, passport.granted.containsKey(note.stamp))
        }
}
