# День 8 — совместимость фоновой загрузки Android API 24–36

- API 34+: добавлен `JobService` и UIDT `JobScheduler` с `RUN_USER_INITIATED_JOBS`, оценкой сетевого размера, системным уведомлением, завершением задания и обработкой отмены/перезапуска.
- API 24–33: добавлен `dataSync` foreground service с немедленным уведомлением. API 29+ использует перегрузку `startForeground` с типом; API 24–28 — совместимую перегрузку без типа.
- UI теперь ставит/отменяет системные задания; загрузку выполняет общий application runtime, поэтому она не зависит от жизненного цикла экрана. При остановке корутина отменяется, а частичный файл с HTTP-валидатором остаётся для следующей попытки.
- В manifest объявлены foreground-service permissions, `RUN_USER_INITIATED_JOBS` и обе неэкспортируемые реализации сервиса. Добавлен канал уведомлений.
- Проверка по Android Developers: UIDT для API 34+, разрешение/тип `dataSync`, разрешение уведомлений FGS без `POST_NOTIFICATIONS` и лимит dataSync на API 35+. Ссылки: [UIDT](https://developer.android.com/develop/background-work/background-tasks/uidt), [изменения FGS](https://developer.android.com/develop/background-work/services/fgs/changes), [таймауты](https://developer.android.com/develop/background-work/services/fgs/timeout), [разрешение уведомлений](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- `compileDebugKotlin` и `assembleDebug` прошли; Gradle обработал merged manifest с новыми разрешениями и службами. `adb devices -l` не обнаружил устройство или эмулятор, поэтому запуск на API 24–36 не проверен.
