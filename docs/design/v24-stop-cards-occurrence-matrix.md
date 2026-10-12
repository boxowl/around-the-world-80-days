# V2.4 — матрица route-occurrence и ограниченная постановка карточек остановок

Дата: 2026-10-12. Read-only (terra-research). Без кода и feature-сессии до проверки матрицы штабом. Учтены замечания: `nodeId=london` не различает старт/финиш; diary-id `departure`/`alps`/`mediterranean` не имеют геометрических узлов; статус `unlocked_diary` — данные v1-экспедиции, в v2 не переносится. Ветка `feat/V24a-stop-cards` не используется (V2.4a — паспорт).

## 1. Факты структуры

`RouteModel.kt`: `RouteNode(id, nameRu, lat, lon, role, chapter, source)`, `RouteLeg(id, fromId, toId, transport, chapter, draftRow, waypointIds, …)`; роли STOP/PORT/CALL/WAYPOINT; `ROUTE_GEOMETRY_VERSION = 1`. `RouteDatasetV1.kt`: **137 узлов** (14 STOP + 11 PORT + 1 CALL + 111 WAYPOINT), **26 RouteLeg** = 24 литературных плеча (06 и 12 разбиты). Явных ID вхождений НЕТ: `london` — `fromId` leg-01 и `toId` leg-24, вхождения различимы только позицией. `RouteMeasure.cumulativeKm()` — только по концам плеч; функции кумулятива до waypoint внутри плеча нет (нужна аддитивная).

## 2. Матрица вхождений для карточек (правило: STOP + PORT + CALL; 111 WAYPOINT — без карточки)

Пороги — регрессионные значения из `RouteGeometryTest` (допуск 1 %); в коде — только вычисление из `RouteMeasure`.

| # | occurrence ID | nodeId | Роль | Порог, книжн. км | Транспорт прибытия |
|--:|---|---|---|---:|---|
| 1 | `london#1` | london | STOP | 0 (старт) | — |
| 2 | `dover#1` | dover | PORT | 108,3 | TRAIN |
| 3 | `calais#1` | calais | PORT | 151,5 | FERRY |
| 4 | `paris#1` | paris | STOP | 388,5 | TRAIN |
| 5 | `turin#1` | turin | STOP | 1 004,2 | TRAIN |
| 6 | `brindisi#1` | brindisi | PORT | 2 011,7 | TRAIN |
| 7 | `port-said#1` | port-said | PORT | 4 189,5 | SHIP |
| 8 | `suez#1` | suez | PORT | 4 339,1 | WATERWAY |
| 9 | `aden#1` | aden | PORT | 6 776,2 | SHIP |
| 10 | `bombay#1` | bombay | STOP | 9 918,4 | SHIP |
| 11 | `kholby#1` | kholby | STOP | 11 072,5 | TRAIN |
| 12 | `allahabad#1` | allahabad | STOP | 11 168,1 | ELEPHANT |
| 13 | `calcutta#1` | calcutta | STOP | 11 922,8 | TRAIN |
| 14 | `singapore#1` | singapore | PORT | 15 167,7 | SHIP |
| 15 | `hong-kong#1` | hong-kong | PORT | 17 833,0 | SHIP |
| 16 | `nagasaki#1` | nagasaki | CALL | ≈20 051 * | SHIP (заход) |
| 17 | `yokohama#1` | yokohama | PORT | 21 327,2 | SHIP |
| 18 | `san-francisco#1` | san-francisco | STOP | 29 916,8 | SHIP |
| 19 | `ogden#1` | ogden | STOP | 30 964,4 | TRAIN |
| 20 | `fort-kearney#1` | fort-kearney | STOP | 32 092,6 | TRAIN |
| 21 | `omaha#1` | omaha | STOP | 32 359,3 | SLEDGE |
| 22 | `chicago#1` | chicago | STOP | ≈33 053 * | TRAIN |
| 23 | `new-york#1` | new-york | STOP | 34 432,0 | TRAIN |
| 24 | `queenstown#1` | queenstown | PORT | 39 741,2 | SHIP |
| 25 | `dublin#1` | dublin | STOP | 39 991,9 | TRAIN |
| 26 | `liverpool#1` | liverpool | PORT | 40 211,9 | SHIP |
| 27 | `london#2` | london | STOP | D = 40 501,8 (финиш) | TRAIN |

\* Особые случаи (порог не на конце плеча, нужна аддитивная интерполяционная функция в `RouteMeasure`): `nagasaki` — CALL как waypoint внутри `leg-15` (≈19 270,9 + ~780); `chicago` — STOP, но геометрически waypoint `leg-20` (32 359,3 + ~694).

Кандидаты-исключения: `shanghai-mouth` (конец leg-14, 19 270,9; в таблице владельца V2.3 — «Шанхай (встреча в море)») и `hooghly-mouth` (конец 12a) — формально WAYPOINT, сюжетно значимы. Вопрос штабу.

## 3. FIRST_LEG diary-id vs геометрические узлы (`Expedition.kt:74–85`; пороги v1 в шагах — шкалы несоизмеримы)

- Совпадают по `id == nodeId` (7): `london`, `dover`, `calais`, `paris`, `turin`, `brindisi`, `suez`.
- Без геометрического узла (3): `departure`, `alps`, `mediterranean` — в v2-карточках не появляются (решение по умолчанию; остаются достоянием v1-дневника).

**Контракт legacy-текст vs v2-открытие:** состояние карточки v2 («достигнуто/впереди») — чистая функция `totalBookMeters(v2) ≥ порог(occurrence)`; `unlocked_diary` для состояния v2 не читается никогда (краш-инвариант `unlocked ⊆ FIRST_LEG`, `Expedition.kt:29`; статус принадлежит v1-экспедиции). Ссылка на legacy-текст дневника как контент — только read-only, только для 7 совпадающих nodeId, только после разрешения владельца; `viewed` и 3 несовпадающих id в v2 не переносятся; у v2 будет своя модель прочитанности.

## 4. Формат occurrence ID

**`<nodeId>#<n>`** — n = порядковый номер вхождения этого nodeId в порядке маршрута (`london#1`, `london#2`; у прочих всегда `#1`). Стабилен при вставке/удалении любых других узлов; меняется только при добавлении более раннего вхождения того же nodeId (в романе повторный узел один). Порог в ID не включать (порог — вычисляемое свойство, меняется при переизмерении). Версионность — не в ID, а в связке с `ROUTE_GEOMETRY_VERSION` (при версии 2 набора карточки пересобираются). Отклонены: `occ-<index>-<nodeId>` (сдвиг индексов при вставке), `<nodeId>@<порог>` (ID меняется при переизмерении).

## 5. Ограниченная постановка (НЕ запускать до проверки матрицы)

- **Цель:** чистая доменная read-модель `GlobeStopCardV1` — 27 карточек по матрице: occurrence ID (`nodeId#n`), nodeId, название, роль, глава, транспорт прибывающего плеча, книжный порог из `RouteMeasure` (включая интерполяцию для nagasaki/chicago), состояние «достигнуто/впереди» — чистая функция от `totalBookMeters`, опциональная read-only ссылка на legacy-текст для 7 совпадающих nodeId. Старт и финиш Лондона — две разные карточки.
- **Границы файлов:** новый `app/src/main/java/com/boxowl/aroundtheworld/expedition/globe/GlobeStopCardV1.kt` + unit-тесты; при утверждении — строго аддитивная функция кумулятива до waypoint в `route/RouteMeasure.kt` (существующие функции не менять); документ.
- **Не трогать:** `RouteDatasetV1.kt`, `Expedition.kt`, `ExpeditionDatabase.kt`/миграции, `unlocked_diary` на запись, пороги/тексты ROUTE.md и FIRST_LEG, UI/навигацию, `path/**` (PR #17), `passport/**` (PR #18), `scene/**`, `health/**`, код PR #12/#14/#19, Gradle/подпись.
- **DoD:** 27 карточек; ссылки на узлы/дневник существуют; пороги из `RouteMeasure`, строго монотонны по порядку вхождений, финиш = D; `london#1` ≠ `london#2` по ID/порогу/главе; состояние: 0 → все кроме `london#1` «впереди», ≥ D → все «достигнуто», ровно порог → «достигнуто»; тест отсутствия карточек у waypoints; сборка/unit/lint зелёные; astra-review привязки/порогов; PR без автослияния. Ветка — новая (не `feat/V24a-stop-cards`), отдельный feature-чат.

## 6. Вопросы к штабу/владельцу

1. `shanghai-mouth` и `hooghly-mouth` — исключить по правилу STOP+PORT+CALL или добавить как исключения?
2. `chicago` (STOP, но mid-leg waypoint) — подтвердить карточку с интерполированным порогом и аддитивную функцию в `RouteMeasure`?
3. Формат `nodeId#n` — утвердить; версионность — связка с `ROUTE_GEOMETRY_VERSION` (не в ID)?
4. `departure`/`alps`/`mediterranean` — подтвердить отсутствие в v2; legacy-ссылка только для 7 nodeId и только после разрешения?
5. Глава вхождения `london#2`: из узла (3) или из прибывающего плеча (XXXII–XXXVI)? Правило для будущих повторных вхождений.
