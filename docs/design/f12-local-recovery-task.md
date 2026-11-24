# F12 — ограничение переноса на другой телефон

- Цель: объяснить пользователю фактическое ограничение локального хранения до сброса или смены телефона.
- Ветка/worktree: `feature/f12-local-recovery`, `/private/tmp/mobile-f12-local-recovery`.
- Границы файлов: `ExpeditionPanel.kt`, `V2JourneyScreen.kt`, `README.md`, `docs/F12_LOCAL_RECOVERY.md`, этот task doc, `TODO.md` для передачи. Не менять backup policy, Room, Health Connect, экспорт, календарь и продуктовые пороги.
- Зависимости: F11b сброс в main; `AndroidManifest.xml` с `allowBackup=false`, Room `expedition.db`, локальные SharedPreferences.
- Готово: тексты v1/v2 и документация точно отражают код; доступны в настройках на эмуляторе; assemble/unit/lint/CI проходят; физическая приёмка шагов не заявляется.
- Цикл: сверить хранение и reset → добавить короткое предупреждение в обе настройки и документ → проверить видимость и сборку → PR/CI → слияние.

## Проверка 2026-11-24

- `AndroidManifest.xml` содержит `allowBackup=false`; Room хранит v1/v2 в `expedition.db`, напоминание — в локальных SharedPreferences. Экспорт и импорт в коде не обнаружены.
- `assembleDebug`, `testDebugUnitTest`, `lintDebug` прошли.
- На Android 16 эмуляторе установлен debug APK: предупреждение видно после прокрутки в настройках v2 и после запуска пустой тестовой v1 экспедиции. Настоящие шаги не проверялись.
