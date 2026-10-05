<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="88" alt="Moshi icon" />

# Moshi · 默识

**An on-device-first personal knowledge base for Android that answers only from your own documents.**

Parsing, chunking, embedding, retrieval and answer generation all run on the phone.
Every answer carries its sources, and when the evidence is not there, Moshi refuses
instead of making something up.

[中文说明](docs/readme/README.zh-CN.md)

![Platform](https://img.shields.io/badge/platform-Android%2026%2B-3DDC84?logo=android&logoColor=white)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a-important)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

</div>

---

## Why

Most "chat with your documents" tools upload your files to a server. Moshi takes the
opposite position: the knowledge base is a private, offline asset. Retrieval never
leaves the device, generation runs in a local model by default, and the cloud engine
is an opt-in that only ever sees your question plus the snippets that retrieval
already selected.

That constraint shapes the whole design — and it is why Moshi is built to say
*"I don't know"* rather than to sound confident.

## Features

- **Local by default.** On-device mode performs no uploads. Parsing, embedding,
  vector search and answer generation are all local.
- **Dual engine, switchable.** Local inference via LiteRT-LM (works out of the box),
  plus an optional OpenAI-compatible cloud engine. The engine badge in the app
  toggles between them with a single tap.
- **Evidence-only answers.** Every answer is grounded in retrieved snippets and
  cites them by number (`【1】`); tapping a source jumps to the original passage and
  highlights the matched span.
- **Refuses instead of fabricating.** When retrieval relevance falls below the
  threshold, the reply is a fixed refusal — `知识库中没有找到相关内容` — never a guess.
- **Sensitive content stays local.** Notes marked sensitive are routed to the local
  engine even when the cloud engine is enabled, and are filtered out of cloud tool
  calls entirely.
- **Multi-format import.** PDF, DOC/DOCX, XLS/XLSX, CSV/TSV, Markdown and plain text,
  plus screenshot OCR (ML Kit, Chinese) and speech transcription (sherpa-onnx).
- **Encrypted at rest.** Optional SQLCipher encryption for both databases and
  AES-GCM for stored source files, with biometric unlock.
- **Knowledge graph view.** A similarity-based graph over your notes, built from the
  same embeddings used for retrieval.

## How it works

### Ingestion

```
file / text / image / audio
        │
        ├─ PDF          → PDFBox-Android
        ├─ DOC(X)/XLS(X) → Apache POI
        ├─ MD / TXT     → commonmark / raw
        ├─ image        → ML Kit OCR (Chinese)
        └─ audio        → sherpa-onnx (SenseVoice, streaming zipformer)
        │
        ▼
   heading- and sentence-aware chunking
        │
        ├─► embedding (MediaPipe Gecko 256 INT8, 768-dim)  → sqlite-vec
        └─► keyword rows                                   → SQLite FTS5 (trigram)
```

### Retrieval

Moshi runs two recall channels and fuses them:

1. **Vector recall** over the sqlite-vec index, with a minimum similarity gate.
2. **Keyword recall** over an FTS5 table using the trigram tokenizer, which keeps
   Chinese substring matching and exact terms (identifiers, numbers, abbreviations)
   working where embeddings are weak.

The channels are combined with **Reciprocal Rank Fusion** (`k = 60`), then filtered by
both an absolute relevance threshold and a ratio relative to the top hit.

Two further steps exist to protect answer quality:

- **Query expansion (pseudo-relevance feedback).** If the first pass looks weak,
  Moshi derives additional queries from the best hit's title and text and retrieves
  again — no extra model call, no added latency for easy questions.
- **Per-note cap.** Only a limited number of chunks from any single note are kept, so
  one long document cannot crowd out everything else in the context window.

### Answering

The retrieved snippets are numbered and handed to the model, which must answer in two
parts — a reasoning section and an answer section — and must cite snippet numbers. If
the snippets do not support an answer, the model is instructed to emit the exact
refusal string, and the client independently verifies that refusal.

Chat history, sources and the exact evidence used are persisted, so an answer can be
audited after the fact.

## Tech stack

| Area | Choice |
| --- | --- |
| Language / UI | Kotlin 2.4.20, Jetpack Compose (Material 3) |
| Architecture | Multi-Activity with tab switching, MVVM, unidirectional `StateFlow` |
| Local inference | LiteRT-LM (`litertlm-android` 0.17.1) |
| Embeddings | MediaPipe Gecko 256 INT8 (768-dim) via `google-localagents-rag` |
| Vector index | sqlite-vec (`SqliteVectorStore`) |
| Keyword index | SQLite FTS5 with the trigram tokenizer |
| Persistence | Room 3 (KSP), SQLCipher 4.19.1 |
| Document parsing | Apache POI 5.5.1, PDFBox-Android 2.0.27.0, commonmark 0.30.0 |
| OCR | ML Kit Text Recognition 16.0.1 (Chinese) |
| Speech | sherpa-onnx 1.13.7 (SenseVoice + streaming zipformer) |
| Cloud (opt-in) | `openai-kotlin` 4.1.0 on Ktor 3.6.0 / OkHttp |
| Images | Coil 3.6.3 |
| Unit tests | JUnit 5 (Jupiter) |
| Build | AGP 9.4.1, Gradle 9.8.0, JDK 21, compileSdk/targetSdk 37, minSdk 26 |

## Architecture

Single Gradle module (`:app`), layered so that UI never touches Room, files or model
APIs directly:

```
app/src/main/kotlin/dev/zlddba/moshiapp/
├── activities/   Activities + Compose UI + ViewModels, grouped per page
├── domain/       Retrieval, Q&A orchestration, tools, chunking, security, prefs
├── data/         Room entities/DAOs, vector store, keyword index, repositories
├── engine/       Local and cloud inference behind a common gateway interface
├── ingest/       Document parsing, OCR, speech, model file management
└── ui/           Design tokens (color/type/shape) and shared UI helpers
```

The local and cloud engines sit behind one abstraction, so models and endpoints stay
replaceable. Retrieval quality, prompt construction and refusal handling are shared by
both engines.

## Getting started

### Requirements

- **JDK 21**
- **Android SDK 37** (compileSdk and targetSdk)
- Android Studio with AGP 9.4.1 or newer
- A **physical arm64-v8a device** running **Android 8.0 (API 26) or newer**

> The app is built for `arm64-v8a` only. It will not install on 32-bit ARM or x86
> devices, and it will not run in most x86 emulators.

### Build and run

```bash
git clone git@github.com:zlddba/moshi.git
cd moshi
./gradlew :app:assembleDebug
```

Or open the `moshi/` directory in Android Studio and press Run. On first sync,
`local.properties` is generated with your `sdk.dir` — it is git-ignored, do not commit it.

### A note on `repo/`

`sherpa-onnx` has no public Maven repository, and JitPack cannot build it — its Android
artifact comes out of the project's own CMake/NDK pipeline. The prebuilt AAR is therefore
**vendored** under `repo/`, which `settings.gradle.kts` declares as a Maven repository:

```
repo/com/github/k2fsa/sherpa-onnx/1.13.7/sherpa-onnx-1.13.7.aar
```

It is checked in deliberately. Without it, a fresh clone cannot resolve
`com.github.k2fsa:sherpa-onnx` and the build fails while configuring. The upstream project
also publishes this artifact on its GitHub Releases, should you ever need to bump it.

## On-device models

Model files are **not** part of this repository. The app downloads them on demand from
ModelScope (with an `hf-mirror.com` fallback) and verifies sizes before use:

| Purpose | Model | File |
| --- | --- | --- |
| Chat (default) | Gemma 4 E2B | `gemma-4-E2B-it.litertlm` |
| Chat (low-end devices) | Qwen3-0.6B | `qwen3_0_6b_mixed_int4.litertlm` |
| Embeddings | Gecko 256 INT8 | `Gecko_256_quant.tflite` + `sentencepiece.model` |
| Speech (offline) | SenseVoice | `model.int8.onnx` + `tokens.txt` |
| Speech (streaming) | Zipformer | `encoder/decoder/joiner-epoch-99-avg-1.int8.onnx` |

Question answering needs a chat model; semantic retrieval needs the embedding model.
The app detects device memory and can downgrade the chat model automatically. Without
an embedding model, retrieval degrades to keyword matching only.

## Cloud engine (optional)

The cloud engine is **off by default** and must be configured explicitly with a base
URL, an API key and a model name. It is OpenAI-compatible, so it works with any
compatible endpoint.

When enabled, the following leaves the device:

- your question, and
- the snippets retrieval already selected from your own knowledge base.

What never leaves the device: your original files, your embeddings, and any note marked
sensitive — those are filtered out before a cloud request is built, including inside
tool calls.

The cloud path also supports function calling with two MCP-shaped tools
(`search_knowledge`, `get_note`) that execute **locally**, so the model can request
more evidence without the knowledge base itself leaving the device. Failures are
surfaced to the user with the specific cause (invalid key, unreachable host, TLS
failure, timeout, HTTP status) rather than being silently swallowed.

## Privacy model

| | Local engine | Cloud engine |
| --- | --- | --- |
| Original files | stay on device | stay on device |
| Embeddings / index | stay on device | stay on device |
| Retrieved snippets | stay on device | sent |
| Question | stays on device | sent |
| Notes marked sensitive | stay on device | never sent |

Databases can be encrypted with SQLCipher, stored source files with AES-GCM, and unlock
can be gated behind biometrics.

## Branching and commits

| Branch | Purpose |
| --- | --- |
| `master` | Stable, demo-ready releases. Merge-only. |
| `develop` | Integration branch. |
| `feature/*` | Feature work, cut from `develop`, merged back with `--no-ff`. |

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
with a Chinese subject line.

## Status

Moshi is under active development. The core loop — import, chunk, embed, retrieve,
answer with citations and refusal handling — is implemented and running on device.
Areas being worked on include retrieval quality tuning, the knowledge graph view and
broader device coverage.

## License

Licensed under the [Apache License 2.0](LICENSE).

## Acknowledgements

Standing on the shoulders of: LiteRT-LM, MediaPipe / `google-localagents-rag`,
sqlite-vec, SQLCipher, Apache POI, PDFBox, ML Kit, sherpa-onnx, Room and Jetpack Compose.
