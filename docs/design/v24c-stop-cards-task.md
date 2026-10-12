# V2.4c — постановка: endpoint-only карточки остановок (первый срез)

Дата: 2026-10-12. Постановка для проверки штабом перед feature-сессией. Без кода и миграций до одобрения. Основание: матрица `docs/design/v24-stop-cards-occurrence-matrix.md`, сужение по решению штаба.

## Объём первого среза

**Ровно 25 карточек**: все STOP/PORT, являющиеся концами плеч `RouteDatasetV1`, плюс два вхождения `london` с уникальными occurrence ID. Исключены до отдельного решения: `nagasaki` (CALL, mid-leg), `chicago` (STOP, но используется в `waypointIds` leg-20 — НЕ конец плеча), `shanghai-mouth`, `hooghly-mouth` и все WAYPOINT.

**Различие ролей (явно, чтобы DoD не противоречил себе):** отбор идёт по «является ли узел концом хотя бы одного плеча», а не только по `role`. `chicago` имеет `role = STOP`, но в маршруте фигурирует как waypoint внутри `leg-20-omaha-new-york` — концом плеча не является, поэтому в срез не входит. `nagasaki` имеет `role = CALL` и тоже mid-leg. Правило отбора: `role ∈ {STOP, PORT} AND узел = fromId или toId хотя бы одного RouteLeg`.

**Пороги:** только существующий `RouteMeasure.cumulativeKm()` (концы плеч), без новых интерполяционных функций и без числовых литералов в коде. Стартовый Лондон — порог 0; финишный — `totalKm()`.

**Без дневника и текстов:** в первом срезе нет ссылок на v1-дневник и сюжетных текстов. Только геометрические данные (nodeId, название, глава, транспорт прибывающего плеча, порог) и derived `reached: Boolean` как чистая функция от `totalBookMeters`.

## Модель

- `GlobeStopCard(occurrenceId: String, nodeId: String, nameRu: String, arrivalChapter: Int, arrivalTransport: Transport?, thresholdBookKm: Double)`
- Occurrence ID: `<nodeId>#<n>` (london#1, london#2; у прочих #1) — по утверждённой матрице.
- **`arrivalTransport: Transport?` — nullable**: у стартового вхождения `london#1` прибывающего плеча нет, значение `null` (не выдумывать транспорт старта). У всех остальных вхождений — `modeToNext` завершающего плеча.
- **`arrivalChapter: Int` — глава прибывающего плеча**, а не глава узла: для `london#2` — глава `leg-24-liverpool-london` (34), а не chapter узла `london` (3). Правило для стартового вхождения: `london#1` получает главу первого плеча маршрута (`leg-01`, chapter 3) — зафиксировано как сознательное соглашение «старт принадлежит главе выезда»; для любых будущих повторных вхождений правило то же: глава = глава прибывающего плеча, у первого вхождения — первого исходящего.
- `reached(card, totalBookMeters)` — чистая функция: `totalBookMeters ≥ card.thresholdBookKm × 1000`. Ровно порог → достигнуто. При 0 — все кроме `london#1` впереди; при ≥ D — все достигнуто.
- Список строится детерминированно из `RouteDatasetV1` + `RouteMeasure`; литералов узлов и порогов нет.

## Границы файлов

- Создать: `app/src/main/java/com/boxowl/aroundtheworld/expedition/globe/GlobeStopCardsV1.kt` (модель + построение списка), `app/src/test/java/com/boxowl/aroundtheworld/expedition/globe/GlobeStopCardsV1Test.kt`, `docs/design/v24c-stop-cards.md` (фактическая запись).
- Read-only: `route/RouteDatasetV1.kt`, `route/RouteMeasure.kt` (БЕЗ добавления функций в этом срезе), `route/RouteModel.kt`.
- **Не трогать:** `route/**` на запись, Room/миграции, `Expedition.kt` и FIRST_LEG, `unlocked_diary`, UI/навигацию, `path/**` (PR #17), `passport/**` (PR #18), `scene/**`, `health/**`, код PR #12/#14/#19, `docs/ROUTE.md`, Gradle/подпись, сюжетные тексты.

## Критерии готовности (DoD)

- Ровно **25 карточек**; каждая — STOP/PORT и конец хотя бы одного плеча (проверка тестом через `legs`).
- `london#1` (порог 0, `arrivalTransport = null`, `arrivalChapter = 3` — глава первого плеча `leg-01`) и `london#2` (порог = `totalKm()`, `arrivalTransport = TRAIN`, `arrivalChapter = 34` — глава прибывающего `leg-24`) — разные occurrence ID, пороги, транспорт и главы прибытия.
- `chicago` и `nagasaki` отсутствуют в списке (явный тест: STOP в `waypointIds` ≠ конец плеча).
- Пороги вычисляются из `RouteMeasure` и строго монотонны по порядку вхождений; финиш = `totalKm()`; тест порогов против `cumulativeKm()` (не литералы).
- `reached`: 0 → только `london#1`; ровно порог → достигнуто; ≥ D → все 25; откат totalBookMeters вниз меняет reached (derived, без памяти).
- Сборка/unit-тесты/lint зелёные; astra-review привязки/порогов/отбора; PR без автоматического слияния.

## Зависимости и блокеры

Зависимостей нет (только утверждённая геометрия main). Не блокирует и не дублирует: PR #17 (визуальные якоря «Пути»), PR #18 (паспорт), PR #19 (позиция точки на полилинии). Отложено на следующие срезы: интерполяция `chicago`/`nagasaki`, исключения `shanghai-mouth`/`hooghly-mouth`, ссылки на legacy-текст, сюжетные тексты. Глава в карточке — прибывающего плеча по правилу выше (вопрос главы `london#2` матрицы закрыт: 34).

## Организация

Ветка `feat/V24c-stop-cards` от main, отдельный worktree и отдельный видимый feature-чат по правилу штаба; sol-implementation → astra-review (отбор endpoints, пороги, отсутствие литералов). Запуск только после одобрения этой постановки штабом.
