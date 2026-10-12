# V2.4c — endpoint-only карточки остановок: фактическая запись

Дата: 2026-10-12. Реализация (sol) по постановке `docs/design/v24c-stop-cards-task.md`. Ветка `feat/V24c-stop-cards`. Файлы: `app/src/main/java/com/boxowl/aroundtheworld/expedition/globe/GlobeStopCardsV1.kt`, `app/src/test/java/com/boxowl/aroundtheworld/expedition/globe/GlobeStopCardsV1Test.kt`, этот документ. `route/**` не изменён (RouteMeasure — без новых функций), Room/Expedition/UI/дневник не затронуты.

## Модель

`GlobeStopCard(occurrenceId, nodeId, nameRu, arrivalChapter, arrivalTransport: Transport?, thresholdBookKm: Double)` — чистая доменная read-модель, без состояния и без ссылок на v1-дневник.

- `occurrenceId` — `<nodeId>#<n>`, n — 1-based номер вхождения узла в порядке маршрута (`london#1`, `london#2`; у прочих `#1`).
- `arrivalChapter` — глава прибывающего (завершающегося на узле) плеча. Для стартового вхождения `london#1` прибывающего плеча нет: зафиксировано соглашение «старт принадлежит главе выезда» — глава первого плеча маршрута leg-01 = 3. Для `london#2` — глава leg-24 = 34 (не глава узла 3).
- `arrivalTransport` — `leg.transport` завершающего плеча; у `london#1` — `null` (транспорт старта не выдумывается).
- `thresholdBookKm` — только из `RouteMeasure.cumulativeKm()`: 0 для старта, кумулятив после прибывающего плеча для остальных, `RouteMeasure.totalKm()` для финиша.
- `GlobeStopCardsV1.reached(card, totalBookMeters)` — чистая функция `totalBookMeters >= thresholdBookKm * 1000`. Ровно порог → достигнуто; откат прогресса вниз меняет результат (памяти нет).

## Правило отбора

Список строится детерминированно из `RouteDatasetV1` + `RouteMeasure`, без литералов узлов и порогов:

1. Обход в порядке маршрута: стартовый узел первого плеча (`fromId` leg-01, порог 0, глава первого плеча, транспорт `null`), затем `toId` каждого из 26 RouteLeg с порогом `cumulativeKm()[i]`, главой и транспортом этого плеча. Каждый узел обхода по построению является `fromId`/`toId` хотя бы одного плеча.
2. Фильтр по роли: остаются только STOP/PORT. Концы плеч с ролью WAYPOINT (`hooghly-mouth`, `shanghai-mouth`) отбрасываются здесь же.
3. Нумерация вхождений `nodeId#n` в порядке обхода.

Итог для набора V1: **ровно 25 карточек** — 24 уникальных endpoint-nodeId с ролью STOP/PORT (14 STOP + 11 PORT = 25 узлов минус `chicago`), из которых `london` даёт два вхождения (23 + 2). Пороги строго монотонны по порядку вхождений, последняя карточка — `london#2` с порогом `totalKm()`.

Не входят (проверено явными тестами):

- `chicago` — роль STOP, но в маршруте фигурирует только в `waypointIds` leg-20; STOP в waypointIds ≠ конец плеча.
- `nagasaki` — роль CALL, mid-leg waypoint leg-15.
- `hooghly-mouth`, `shanghai-mouth` — концы плеч, но роль WAYPOINT.
- все 111 WAYPOINT-узлов геометрии.

## Отличия от матрицы 27 карточек (`v24-stop-cards-occurrence-matrix.md`)

Матрица V2.4 строилась по правилу STOP+PORT+CALL и давала 27 карточек. Срез V2.4c — сужение по решению штаба:

| Матрица (27) | V2.4c (25) |
|---|---|
| `nagasaki#1` (CALL, интерполяция внутри leg-15) | исключена |
| `chicago#1` (STOP, но mid-leg waypoint leg-20, интерполяция) | исключена |
| Правило STOP+PORT+CALL | Правило STOP/PORT **и** конец плеча |
| Нужна аддитивная интерполяционная функция в `RouteMeasure` | Не нужна: только существующий `cumulativeKm()` |
| `london#2`: глава — открытый вопрос | Закрыт: 34 (глава прибывающего leg-24) |

Отложено на следующие срезы: интерполяция `chicago`/`nagasaki`, судьба `shanghai-mouth`/`hooghly-mouth`, ссылки на legacy-текст дневника, сюжетные тексты, любая персистентность.

## Проверки

Unit-тесты `GlobeStopCardsV1Test` (8 тестов): ровно 25 карточек с уникальными occurrence ID; каждая карточка — STOP/PORT и конец хотя бы одного плеча (через `RouteDatasetV1.legs`); `london#1` (0, `null`, 3) ≠ `london#2` (`totalKm()`, TRAIN, 34) по ID/порогу/транспорту/главе; отсутствие `chicago`/`nagasaki` с явной проверкой различия STOP-в-waypointIds ≠ конец плеча (и отсутствие `hooghly-mouth`/`shanghai-mouth`); пороги равны `cumulativeKm()` по индексу прибывающего плеча, строго монотонны, финиш = `totalKm()`; `reached`: 0 → только `london#1`, ровно порог → достигнуто, на метр ниже → нет, ≥ D → все 25, откат прогресса меняет результат без памяти.

Сборка/тесты/lint: `ANDROID_HOME=/Users/maximpacts/Library/Android/sdk ./gradlew --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` — зелёные (числа — в отчёте sol штабу). Далее — обязательный astra-review отбора/порогов/отсутствия литералов; PR не открыт.
