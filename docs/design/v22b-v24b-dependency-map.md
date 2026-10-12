# V2.2b + V2.4b — карта зависимостей долговечного прогресса на Room schema v3

Дата: 2026-10-12. Read-only блок (terra-research, agent-35), сверено с `ExpeditionDatabase.kt` и контрактами `v22b-proposal.md` / `v24b-passport-storage.md`. Без кода и миграций; источник метров не утверждён до физической проверки. PR #17/#18/#19 не сливать автоматически.

## 1. Хранение на схеме v3 (рекомендация)

Сейчас: `expedition` (1 строка), `daily_steps`, `unlocked_diary`, version 3, `exportSchema = false`.

| Сущность | Размещение | Аргумент |
|---|---|---|
| Дневной зачёт v2 | новая таблица `daily_credit(date TEXT PK, real_meters INTEGER)` | контракт V2.2b; книжные метры не хранить (производные ×20, k применяется один раз — `DayCredit.kt:14`, `CreditLedger.kt:39`) |
| Параметры v2 (routeVersion=2, geometryVersion, strideLength, sourceKind, k, startedAt, zone) | новая однострочная `expedition_v2(id=1, …)` | «отдельный агрегат, не расширение Expedition» (`v22b-proposal.md:22`); колонки в `expedition` отклонены ранее — v1-строка не трогается. Имя entity — решение штаба |
| Штампы | новая таблица `passport_stamps(stampId TEXT PK, grantedAt TEXT NULL)` | контракт V2.4b; `unlocked_diary` крашит v1-загрузку (`unlocked ⊆ FIRST_LEG`, `Expedition.kt:29` + `ExpeditionDatabase.kt:130`) |
| Базовая линия | новая однострочная `passport_meta(id=1, baselineBookMeters)` | контракт V2.4b |

**Сосуществование v1/v2:** `Expedition.init` крашится на `routeVersion != 1` (`Expedition.kt:23`), поэтому **v2 не пишет в таблицу `expedition` вообще**; v2-агрегат живёт в своих таблицах, v1-путь его не знает. DAO — отдельные `CreditDao`/`PassportDao`, v1-DAO не разрастается.

## 2. Восстановление snapshot при холодном старте (v2)

1. `passport_stamps` → `PassportSnapshot` (неизвестные id — пропуск, не удаление) + `passport_meta.baselineBookMeters`.
2. `daily_credit` → `CreditLedger.of(map)` → `totalBookMeters`. **Долговечный итог не хранится** — всегда вычисляется из суммы строк (модель без накопителя; хранимая колонка итога создала бы второй источник истины).
3. Двухпроходный reconcile по базовой линии в одной транзакции: `reconcile(existing, min(total, baseline), null)` → `reconcile(snapshot1, total, now)` → дозапись (INSERT IGNORE) + `baseline := max(baseline, total)` атомарно; при откате baseline назад не двигается.
4. v1-экспедиция не читает и не получает ничего из новых таблиц.

## 3. Связь с контуром Health Connect

Контур `ExpeditionSync.kt:33-67` переиспользуется: окна в поясе экспедиции, первый день от `startedAt`, окно скана 119 дней, `null` = gap (день неизменён), отзыв доступа посреди скана — ничего не пишется, полный провал — `ReadError`, stale-guard `lastReadAt`. Метры v2 = `steps × strideLength` с пометкой `sourceKind` («расчётные»); **Distance не утверждён**; контракт шлюза (`StepTotal(count, origins)`) не меняется. Граница истории HC — 30 дней до первой выдачи разрешения; `READ_HEALTH_DATA_HISTORY` не предлагать.

## 4. Миграции и тесты

- `room-testing` подключён (`app/build.gradle.kts:30`); прецедент теста обновления — ручная старая схема через `SQLiteDatabase` (`ExpeditionSchemaV2Test.kt:23-54`); `exportSchema = false` — MigrationTestHelper неприменим.
- **Рекомендация: одна совместная аддитивная миграция v3→v4** (4 × `CREATE TABLE`), если задачи готовы близко: одна цепочка v1→v2→v3→v4 в тестах, один astra-review. Правило «кто первый сливается — v3→v4, вторая v4→v5» остаётся запасным; **владельца v3→v4 штаб назначает до старта кода** и закрепляет в TODO.
- Обновление APK поверх v1 — по прецеденту P08 (ручная установка поверх предыдущей сборки).

## 5. Следующая задача (предложение): объединённая V2.2b+V2.4b

**Обоснование объединения:** общая миграция, общий v2-репозиторий, общее решение о месте агрегата; V2.4b без долговечного `totalBookMeters` пользовательски готовой не объявляется.

- **Цель:** долговечный прогресс v2 и паспорт на схеме v3: таблицы `daily_credit`, `expedition_v2`, `passport_stamps`, `passport_meta`; `CreditStore` (маппинг строк ↔ CreditLedger, sync-вход метров); `PassportStore` (маппинг, двухпроходный reconcile, атомарная транзакция); восстановление после перезапуска.
- **Границы файлов (можно):** `ExpeditionDatabase.kt` (новые entity/DAO, MIGRATION_3_4 — строго аддитивно); новые `expedition/credit/CreditStore.kt`, `expedition/passport/PassportStore.kt`; unit + androidTest; фактические правки `v22b-proposal.md`/`v24b-passport-storage.md`.
- **Не трогать:** v1-путь целиком (`Expedition.kt`, v1-ветки repository/sync/ViewModel); модель V2.4a из PR #18 до её слияния (вход только после мержа); `CreditLedger.kt`/`DayCredit.kt` (кроме интеграционных точек); `route/**`, `path/**`, `scene/**`, контракты `health/**`; навигацию и UI «Паспорт» (отдельная задача V2.4); пороги/тексты ROUTE.md; код PR #12/#14/#17/#19; Gradle/подпись.
- **DoD:** восстановление CreditLedger и набора штампов после перезапуска (равенство снимков, включая NULL-даты); двухпроходный reconcile; первое восстановление с прогрессом > 0 → все штампы NULL, baseline атомарно; скачок через исторический и новый пороги за один вызов (NULL + observedAt, идемпотентно); откат не отзывает штампы и не двигает baseline назад; null ≠ 0 и guards HC сохранены тестами; обновление поверх v1 без потери данных (цепочка v1→v4 на ручной старой схеме); v1 загружается, штампов не получает; существующие тесты зелёные без изменения ожиданий; **обязательный astra-review миграций, базовой линии и расчётов**; PR без автослияния.
- **Точка вызова reconcile: репозиторий v2 (CreditStore/PassportStore), не ViewModel** — транзакционность «дозапись + baseline» требует слоя БД; ViewModel читает готовый снимок.

## 6. Блокеры и порядок

1. **Слияние PR #18** — жёсткая предпосылка V2.4b (модели в main нет — подтверждено grep).
2. **Источник метров и strideLength** — утверждение владельцем; блокирует только заполнение `sourceKind`/расчёт метров, не схему. Distance не утверждать; физическая приёмка — P02/F02, вне этой задачи.
3. **Владелец миграции v3→v4** — назначить штабом до старта кода; закрепить правило в TODO.
4. **Открытые продуктовые вопросы, в коде не закреплять:** поток старта v2, пари для v2, формат даты штампа (ISO-8601 vs epoch ms), day-precision backfill, отображение штампа без даты, единица цикла освещения.

**Рекомендуемый порядок:** решение владельца по PR #18 и stride → штаб назначает владельца v3→v4 и подтверждает точку вызова (репозиторий v2) → полная постановка объединённой задачи в отдельном видимом чате → sol-implementation → astra-review → PR без автослияния.
