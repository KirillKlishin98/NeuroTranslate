# Day 4 — ONNX Runtime и Marian encoder/decoder

Дата реализации: 2026-10-27.

## Изменения

- Подключена закреплённая зависимость `com.microsoft.onnxruntime:onnxruntime-android:1.31.0`.
- Добавлен контракт `TranslationEngine` на входных token IDs и реализация `MarianOnnxEngine` для локальных encoder/decoder файлов.
- Перед загрузкой сессий engine проверяет SHA-256 обоих файлов по значениям, переданным вызывающим кодом, и проверяет входные/выходные имена графов.
- Encoder выполняется один раз на вход; decoder делает greedy-шаги по logits, останавливается на `eos_token_id` и ограничивает число новых токенов значением не выше `max_length - 1`.
- Запрашиваются только logits; дополнительные KV cache outputs не копируются в результат вызова.
- Сессии, временные входные тензоры и результаты закрываются явно. Операции движка сериализованы локальной блокировкой; `TranslationService` остаётся следующим архитектурным этапом.
- Добавлено R8 keep-правило для классов ONNX Runtime.

## Проверка и ограничения

- Проверка источников модели и фактических графов выполнена на дне 3: EN → RU opset 11, входы encoder/decoder и выход logits совпадают с контрактом.
- Debug-сборка прошла. Устройство доступно в Android SDK, но запуск adb в изолированной среде заблокирован, поэтому фактический ONNX-прогон на Android не подтверждён.
- Текстовый ввод пока нельзя переводить из экрана: SentencePiece токенизатор ещё не реализован, модель нельзя установить из приложения, `TranslationService` не подключён.
- Веса моделей остаются за пределами Git.

- Verification result: `assembleDebug` succeeded. The debug APK is 162,689,668 bytes and contains ORT native libraries for four ABIs; no ONNX weights are packaged. This is not a release-size measurement.
