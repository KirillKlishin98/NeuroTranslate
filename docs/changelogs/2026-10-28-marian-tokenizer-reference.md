# День 5 — аудит Marian SentencePiece и эталонные случаи

- Скачаны только tokenizer/config файлы двух закреплённых Xenova ревизий; ONNX-веса не затрагивались.
- Подтверждены размеры и SHA-256 `source.spm` и `target.spm`; RU → EN использует их в обратном порядке относительно EN → RU.
- Сгенерирован `app/src/test/resources/marian-tokenizer-reference.json`: 100 source/target примеров на направление через Hugging Face Transformers MarianTokenizer 4.41.2, SentencePiece 0.2.0 и Sacremoses 0.1.1.
- Обновлён каталог моделей; в нём отмечено, что эталон есть, но Android-runtime и проверка Kotlin parity отсутствуют.
- DJL SentencePiece не выбран: в опубликованном Android runtime не обнаружены Android native библиотеки SentencePiece, а DJL Android указывает min API 26 при minSdk 24 проекта. Android NDK в установленном SDK также отсутствует. Непроверенный pure-Java порт не использован.
- Этап не считается завершённым: требуется выбрать поддерживаемый Android runtime, реализовать source encode/target decode и прогнать ID parity тесты на эталонном наборе.
