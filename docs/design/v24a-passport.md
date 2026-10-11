# V2.4a — доменная модель паспорта путешественника (чистая, без хранения)

Дата: 2026-10-11. Ветка `feat/V24a-passport`, worktree `mobile-v24a`. Постановка: `docs/design/v24a-passport-task.md`. Реализация: `app/src/main/java/com/boxowl/aroundtheworld/expedition/passport/PassportV1.kt`, тесты `app/src/test/java/com/boxowl/aroundtheworld/expedition/passport/PassportV1Test.kt`.

## Модель

Ровно **9 отметок**: стартовый Лондон + 8 финишей глав. `StampId` — enum со стабильным строковым `id` (зафиксирован контрактными тестами; на него будет опираться хранение V2.4b — менять нельзя) и геометрическим `nodeId`. Старт и финиш Лондона имеют один `nodeId` (`london`), но **разные StampId и разные пороги** (0 и D).

| StampId | Строковый id | nodeId | Источник порога |
|---|---|---|---|
| LONDON_START | `stamp-london-start` | `london` | 0 книжных метров |
| SUEZ_FINISH | `stamp-suez-finish` | `suez` | кумулятив после `leg-06b-port-said-suez` |
| BOMBAY_FINISH | `stamp-bombay-finish` | `bombay` | кумулятив после `leg-08-aden-bombay` |
| CALCUTTA_FINISH | `stamp-calcutta-finish` | `calcutta` | кумулятив после `leg-11-allahabad-calcutta` |
| HONG_KONG_FINISH | `stamp-hong-kong-finish` | `hong-kong` | кумулятив после `leg-13-singapore-hong-kong` |
| YOKOHAMA_FINISH | `stamp-yokohama-finish` | `yokohama` | кумулятив после `leg-15-shanghai-mouth-yokohama` |
| SAN_FRANCISCO_FINISH | `stamp-san-francisco-finish` | `san-francisco` | кумулятив после `leg-16-yokohama-san-francisco` |
| NEW_YORK_FINISH | `stamp-new-york-finish` | `new-york` | кумулятив после `leg-20-omaha-new-york` |
| LONDON_FINISH | `stamp-london-finish` | `london` | D = `RouteMeasure.totalKm()` |

Пороги — функция от геометрии маршрута (`RouteDatasetV1` + `RouteMeasure`), вычисляются при загрузке класса `PassportV1`, литералов нет. Плечо-конец главы ищется по точному id; отсутствие id в датасете — fail fast (ошибка при инициализации класса, не тихий пропуск). Единицы — книжные метры: кумулятив `RouteMeasure.cumulativeKm()` (книжные км) × 1000. Коэффициент реальных метров в книжные (k = 20) здесь **не применяется** — он уже учтён раньше в `CreditLedger.totalBookMeters`, на вход `reconcile` подаётся книжный прогресс.

## API

```kotlin
enum class StampId(val id: String, val nodeId: String) { /* 9 значений, порядок = порядок маршрута */ }

class PassportSnapshot private constructor(val granted: Map<StampId, Instant?>) {
    companion object { fun of(granted: Map<StampId, Instant?> = emptyMap()): PassportSnapshot }
}

object PassportV1 {
    fun thresholdBookMeters(stampId: StampId): Double
    fun reconcile(existing: PassportSnapshot, totalBookMeters: Long, observedAt: Instant? = null): PassportSnapshot
}
```

`PassportSnapshot.granted`: наличие ключа — «штамп выдан», значение — момент выдачи, `null` — дата неизвестна. Снимок глубоко неизменяем по образцу `CreditLedger`: `of` делает защитную копию, exposed map — `Collections.unmodifiableMap`; `reconcile` возвращает новый снимок и не мутирует вход. Неизвестные StampId невозможны типобезопасно (ключи карты — enum), поэтому отдельной валидации строковых id нет; строковые значения зафиксированы тестом.

## Инварианты монотонности `reconcile`

- Результат = **union** переданного набора и штампов, чьи пороги достигнуты; переданный набор никогда не уменьшается — откат прогресса (в т.ч. поздняя корректировка `CreditLedger`) **не отзывает** выданные штампы. Семантика согласована с `unlocked_diary` (IGNORE) и правилом «открытое не откатывается».
- Даты переданных штампов **не переписываются**: ни существующая дата, ни существующий `null` не заменяются новым `observedAt`.
- Новое пересечение получает переданный `observedAt` (может быть `null`). Задним числом дату из одного текущего итога не выводить.
- Идемпотентность: `reconcile(reconcile(x, m, t), m, t) == reconcile(x, m, t)` (equality по содержимому).
- `totalBookMeters < 0` — `IllegalArgumentException`.
- При скачке через несколько глав выдаются все промежуточные штампы; стартовая отметка выдаётся при нулевом прогрессе (порог 0).

## Правило границы (Long против Double)

Порог хранится как `Double` в книжных метрах и **не округляется** до сравнения; скрытого допуска нет. Штамп выдаётся тогда и только тогда, когда `totalBookMeters.toDouble() >= thresholdBookMeters`. Прогресс (≤ ~40,6 млн метров) точно представим в Double, поэтому граница детерминирована: например, порог Суэца ≈ 4 339 152,49 книжных метров (значение округлено для чтения; точный порог вычисляется из геометрии и не округляется), значит 4 339 152 м не хватает, а 4 339 153 м достаточно; финальный порог ≈ 40 501 820,77, достаточно 40 501 821 м.

## Не проверено в V2.4a (критерии V2.4b, проверенными не заявляются)

- Реальное **хранение** штампов (таблица/строки, формат сериализации).
- **Восстановление** паспорта после перезапуска приложения.
- **Backfill** исторических дат выдачи и выбор **источника календаря** для `observedAt`.
- **Миграция БД** под хранение паспорта.
- **UI-интеграция** (вкладка «Паспорт», художественный дизайн — отдельная UI-задача).

Модель V2.4a работает с любым будущим вариантом хранения через `PassportSnapshot` на входе `reconcile`.
