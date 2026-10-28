"""Generate pinned MarianTokenizer fixtures used to verify Kotlin SentencePiece parity.

Run with transformers==4.41.2 and huggingface_hub==0.23.4. The two tokenizer
snapshots are downloaded by immutable revision into the ignored model-audit cache.
"""

import hashlib
import json
from pathlib import Path

from huggingface_hub import snapshot_download
from transformers import MarianTokenizer


ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / ".gradle" / "model-audit"
OUTPUT = ROOT / "app" / "src" / "test" / "resources" / "marian-tokenizer-reference.json"
MODELS = {
    "en-ru": ("Xenova/opus-mt-en-ru", "88735d14188d480cade09dce9f662e60e86effbf"),
    "ru-en": ("Xenova/opus-mt-ru-en", "4c3e82163683bb911e3803469ebdee03c8b65f0a"),
}
FILES = [
    "source.spm",
    "target.spm",
    "tokenizer.json",
    "tokenizer_config.json",
    "vocab.json",
    "special_tokens_map.json",
    "config.json",
    "generation_config.json",
]

ENGLISH = [
    "Hello, world!", "Good morning.", "How are you?", "I love learning languages.",
    "Where is the nearest station?", "Please translate this sentence.",
    "The weather is sunny today.", "Can you help me?", "Thank you very much.",
    "I do not understand.", "This book belongs to my friend.", "We will meet again tomorrow.",
    "The train leaves at half past six.", "Could you speak more slowly, please?",
    "My phone battery is almost empty.", "She bought fresh apples at the market.",
    "They have lived here for three years.", "Turn left after the old bridge.",
    "I would like a cup of tea without sugar.", "What time does the museum open?",
]
RUSSIAN = [
    "Привет, мир!", "Доброе утро.", "Как дела?", "Я люблю изучать языки.",
    "Где ближайшая станция?", "Пожалуйста, переведи это предложение.",
    "Сегодня солнечная погода.", "Ты можешь мне помочь?", "Большое спасибо.",
    "Я не понимаю.", "Эта книга принадлежит моему другу.", "Мы встретимся завтра.",
    "Поезд отправляется в половине седьмого.", "Говорите, пожалуйста, помедленнее.",
    "У телефона почти разрядился аккумулятор.", "Она купила свежие яблоки на рынке.",
    "Они живут здесь уже три года.", "Поверните налево за старым мостом.",
    "Я хотел бы чашку чая без сахара.", "Во сколько открывается музей?",
]
EDGE_CASES = [
    "  multiple   spaces  ", "First line\nsecond line", "Tab\there", "", "12345 67.89",
    "Café, naïve, déjà vu.", "Unicode: ёж, €5, and ™.", "Emoji 😀 should remain text.",
    "Don't stop—keep going!", "A/B; C:D (E) [F] {G}", "ＡＢＣ and ﬁ ligature",
    "e\u0301 versus é", "Questions?! ... really?", "New\r\nWindows line",
    "The word 'model' appears twice: model.", "co-operate; re-enter; well-known",
    "Numbers: 0, 1, 2, 10, 100, 1000.", "Quoted text: ‘hello’ and \"world\".",
    "Keep case: ABC xyz MiXeD.", "A long sentence checks token boundaries across several common words.",
]


def corpus(bases: list[str]) -> list[str]:
    samples = [f"{base} (sample {index + 1})" for index, base in enumerate(bases)]
    samples.extend(EDGE_CASES)
    samples.extend(f"{base} — вариант {index + 1}." for index, base in enumerate(bases[:20]))
    assert len(samples) == 60
    samples.extend(f"{bases[index % len(bases)]} #{index + 61}" for index in range(40))
    assert len(samples) == 100
    return samples


def tokenizer_snapshot(repo: str, revision: str, key: str) -> tuple[MarianTokenizer, Path]:
    path = snapshot_download(
        repo_id=repo,
        revision=revision,
        allow_patterns=FILES,
        local_dir=CACHE / key,
    )
    return MarianTokenizer.from_pretrained(path, local_files_only=True), Path(path)


def encode(tokenizer: MarianTokenizer, text: str, *, target: bool = False) -> list[int]:
    result = tokenizer(text_target=text) if target else tokenizer(text)
    return result["input_ids"]


def file_manifest(root: Path) -> dict[str, dict[str, int | str]]:
    manifest = {}
    for name in FILES:
        path = root / name
        if path.is_file():
            contents = path.read_bytes()
            manifest[name] = {
                "sizeBytes": len(contents),
                "sha256": hashlib.sha256(contents).hexdigest(),
            }
    return manifest


def main() -> None:
    pairs = {}
    for key, (repo, revision) in MODELS.items():
        tokenizer, root = tokenizer_snapshot(repo, revision, key)
        source_language, target_language = key.split("-")
        source_samples = corpus(ENGLISH if source_language == "en" else RUSSIAN)
        target_samples = corpus(RUSSIAN if target_language == "ru" else ENGLISH)
        examples = [
            {
                "sourceText": source,
                "sourceIds": encode(tokenizer, source),
                "targetText": target,
                "targetIds": encode(tokenizer, target, target=True),
            }
            for source, target in zip(source_samples, target_samples, strict=True)
        ]
        pairs[key] = {
            "repository": repo,
            "revision": revision,
            "tokenizerClass": type(tokenizer).__name__,
            "eosTokenId": tokenizer.eos_token_id,
            "padTokenId": tokenizer.pad_token_id,
            "sourceSpmSha256": file_manifest(root)["source.spm"]["sha256"],
            "targetSpmSha256": file_manifest(root)["target.spm"]["sha256"],
            "files": file_manifest(root),
            "examples": examples,
        }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "reference": {
                    "implementation": "Hugging Face Transformers MarianTokenizer",
                    "version": "4.41.2",
                    "sentencepieceVersion": "0.2.0",
                    "sacremosesVersion": "0.1.1",
                },
                "pairs": pairs,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {OUTPUT.relative_to(ROOT)} with 100 cases for each pair.")
    for key, value in pairs.items():
        print(key, json.dumps(value["files"], ensure_ascii=False))


if __name__ == "__main__":
    main()
