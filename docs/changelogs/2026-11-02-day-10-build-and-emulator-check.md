# День 10 — сборки и проверка на эмуляторе

- Собраны debug и release варианты; Release R8 и lint vital завершились успешно.
- Debug APK установлен и запущен на AVD `Medium_Phone` (Android API 37). `MainActivity` отображалась на переднем плане; в просмотренном фрагменте logcat `FATAL EXCEPTION` не найден.
- Сохранён снимок экрана `docs/screenshots/day10-medium-phone-api37.png`.
- Замер idle-состояния через 12 секунд после открытия: 129,734 KiB PSS и 257,280 KiB RSS. ONNX-модель не активировалась, поэтому эти числа не описывают перевод/память модели.
- Размеры APK: debug 163,818,930 байт (156.2 MiB), release unsigned 144,627,960 байт (137.9 MiB). AGP сообщил, что четыре предварительно собранные native-библиотеки не удалось strip-нуть.
- Сквозной перевод, измерения активной модели и Android instrumentation test не проверялись: экран не подключён к engine, токенизатор отсутствует в Android runtime.
