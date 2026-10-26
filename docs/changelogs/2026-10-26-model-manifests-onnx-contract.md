# Day 3 — закрепление моделей и ONNX-контракта

Дата проверки: 2026-10-26.

## Выбрано

- EN → RU: `Helsinki-NLP/opus-mt-en-ru`, лицензия Apache-2.0; ONNX-конверсия `Xenova/opus-mt-en-ru`.
- RU → EN: `Helsinki-NLP/opus-mt-ru-en`, лицензия CC-BY-4.0; ONNX-конверсия `Xenova/opus-mt-ru-en`.
- Зафиксированы полные commit SHA обеих исходных и обеих ONNX-ревизий в `app/src/main/assets/models/catalog.json`.
- Выбраны отдельные INT8/QInt8 encoder и decoder модели без объединённого decoder-файла. Размер ONNX-пары — 110,189,320 байт (примерно 105.1 MiB) для каждого направления; сюда не входят файлы токенизатора.

## Фактическая проверка артефактов

Четыре ONNX-файла были скачаны по закреплённым ревизиям во временную папку `.gradle/model-audit` (она игнорируется Git). Размеры и SHA-256 рассчитаны локально по файлам, скачанным с URL закреплённых ревизий. Protobuf-структура ONNX прочитана для определения входов, выходов, типов, форм и opset.

- Opset 11 для encoder и decoder.
- Encoder: `input_ids` и `attention_mask` — int64 `[batch, source_length]`; выход `last_hidden_state` — float32 `[batch, source_length, 512]`.
- Decoder: входы `encoder_attention_mask`, `input_ids`, `encoder_hidden_states` с ожидаемыми типами и размерами; выход `logits` — float32 `[batch, target_length, 62518]`. Также граф содержит 24 выхода `present.*` для KV cache.
- Для обеих моделей конфигурация задаёт `decoder_start_token_id=62517`, `eos_token_id=0`, `pad_token_id=62517`, `max_length=512`; `d_model=512`, словарь 62,518, по 6 encoder/decoder слоёв.

## Что не завершено этим этапом

- Это проверка структуры и контрольных сумм артефактов, не запуск ONNX Runtime и не проверка качества перевода.
- Установка модели и загрузчик не реализованы.
- В каталоге указаны файлы токенизатора из репозиториев, но проверка их хешей и точности MarianTokenizer отложена до этапа токенизатора. Не считай `tokenizer.json` подтверждённым эталоном.
- Пакет нельзя считать готовым к распространению до проверки лицензированных файлов токенизатора/атрибуции и сквозной runtime-проверки.
