# V2.4e — пользовательский глобус для активной v2

Старт: 2026-11-12. Ветка `feature/v24e-production-globe`, worktree `/private/tmp/mobile-v24e-production-globe`, база `073505f` после PR #33. Источники: `docs/design/v21-globe-proto.md`, `docs/design/v24d-debug-stop-cards-task.md`, `docs/design/v2-product-decisions.md`, `docs/SPEC.md`, `V2JourneyScreen.kt`.

## Цель

На вкладке «Карта» активной v2 показывать глобус, положение героя и пройденный/будущий маршрут из сохранённого книжного прогресса. Карточки остановок остаются доступными. Данные синтетического debug-сценария не должны попадать в пользовательский экран.

## Карта контрактов и границы

Debug-рендер разбит на `GlobeMath`, `GlobeScene`, `CoastlineData`, `RouteData`, `RoutePosition` и `GlobeSliceActivity`; береговая геометрия `app/src/debug/assets/ne_land.bin` (Natural Earth) отделена от production. `RoutePosition` уже берёт версионированный `RouteDatasetV1`, но возвращает типы debug-пакета. Поэтому первый шаг — выделить общий pure/Compose рендер и реальные route adapter из debug без копии формул; затем UI берёт только `V2CreditSnapshot.ledger.totalBookMeters`. Карточки `GlobeStopCardsV1` уже в main.

Разрешены новые `app/src/main/java/com/boxowl/aroundtheworld/expedition/globe/**`, перенос/адаптация указанных пяти debug helper-файлов и их тестов, перенос лицензированного `ne_land.bin` и README в `src/main/assets`, точечные изменения `GlobeSliceActivity.kt` и `V2JourneyScreen.kt`, тесты, этот документ и `TODO.md`. Не менять Room, Health Connect, `CreditLedger`, календарь/паспорт, геометрию `RouteDatasetV1`/`RouteMeasure`, пороги и дневник. Если потребуется другая архитектура файлов, сначала обновить границы здесь.

## Критерии готовности

1. Production карта строится из сохранённого v2 book km; пустая экспедиция — Лондон, при корректировке назад путь и герой отступают. Ни debug extras, ни синтетические метры не используются.
2. Геометрия маршрута, антимеридиан, транспортные сегменты и финиш совпадают с проверенным debug-прототипом без второго источника порогов. Визуальный просмотр прошлого не меняет сохранённые данные; если жест переносится отдельно, этот релиз показывает подтверждённую позицию.
3. 25 готовых endpoint-карточек доступны с глобуса через понятный список; недостигнутые не выдаются за открытые. Освещение по расчётным реальным километрам подключается через существующий `DistanceLighting`, без зависимости от времени суток.
4. JVM-тесты проекции/границ, assemble/unit/lint, эмуляторная UI-проверка и отдельное строгое ревью геометрии/межмодульной интеграции; CI зелёный до автоматического слияния. P02/F02/Q01 остаются на физическом устройстве.

Порядок: карта зависимостей и лицензии assets → выделение общего рендера без изменения формул → production adapter → экран → тесты и визуальная проверка → строгий review → PR/CI/merge.

## Проверенный первый шаг: зависимости и происхождение

- `ne_land.bin` лежит только в `src/debug/assets`; локальный README фиксирует Natural Earth `ne_50m_land`, 616 полигонов/11 031 точек, упрощение и формат. [Официальные условия Natural Earth](https://www.naturalearthdata.com/about/terms-of-use/) прямо разрешают модификацию и распространение этих данных как public domain. Переносить вместе с README и указанием происхождения.
- `GlobeMath.kt` и `CoastlineData.kt` не используют синтетический маршрут и подходят для общего слоя; `GlobeScene.kt` сейчас зависит от типа `RouteData.Route`, а `RouteData.kt` содержит тестовые координаты и не должен целиком попадать в release.
- `RoutePosition.kt` уже измеряет `RouteDatasetV1` и строит маршрут по `RouteMeasure`, но возвращает типы из debug-пакета. Следующий кодовый шаг: выделить только типы `Route`/`Transport` в production adapter и перевести `GlobeScene`/`RoutePosition` на них; оставить тестовые `RouteData.nodes` в debug. Проверить старые debug-тесты после механического переноса до подключения UI.
- `GlobeSliceActivity` содержит и проверенный Canvas, и диагностический/синтетический экран; в production переносится только Canvas-рисование. `SliceScenario`, extras и диагностический баннер остаются в debug.

## Проверенный шаг: общая геометрия и asset

`GlobeMath.kt` и `CoastlineData.kt` перенесены из debug в `src/main/.../expedition/globe` без изменения формул; debug-прототип и существующие JVM-тесты теперь импортируют общий код. `ne_land.bin` и его README перенесены в `src/main/assets`, поэтому production сможет читать тот же набор береговых линий. После переноса `assembleDebug`, `testDebugUnitTest`, `lintDebug` прошли. Это инфраструктурный шаг: `GlobeScene`/`RoutePosition` и production UI пока остаются в debug, карта v2 всё ещё показывает карточки.

Следующий шаг — общий route adapter: вынести типы маршрута из `RouteData` без синтетических `nodes`, перевести `RoutePosition` и `GlobeScene` на них, сохранить работу debug-прототипа и его тестов.
