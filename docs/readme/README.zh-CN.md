<div align="center">

<img src="../../app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="88" alt="默识图标" />

# 默识 · Moshi

**端侧优先的 Android 个人知识库，只依据你自己的文档作答。**

解析、分块、嵌入、检索与答案生成全部在手机上完成。每条回答都强制附带来源；
当依据不足时，默识会直接拒答，而不是编造内容。

[English README](../../README.md)

![平台](https://img.shields.io/badge/%E5%B9%B3%E5%8F%B0-Android%2026%2B-3DDC84?logo=android&logoColor=white)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a-important)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![许可证](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-Apache--2.0-blue)

</div>

---

## 为什么做这个

多数「和你的文档对话」的工具会把文件上传到服务器。默识的立场相反：知识库是私有的、
离线的资产。检索永不出端，生成默认由端侧模型完成；云端引擎是需要用户显式开启的选项，
且只会看到你的问题与检索已经选中的片段。

这条约束决定了整个设计，也是默识宁可说「不知道」、也不装作有把握的原因。

## 核心特性

- **默认本地。** 端侧模式不做任何上传，解析、嵌入、向量检索与答案生成全部在本机。
- **双引擎可切换。** 端侧推理基于 LiteRT-LM（开箱即用），另可选配 OpenAI 兼容的云端
  引擎。页面上的引擎徽标点击即可切换。
- **有据才答。** 每条回答都由检索命中的片段支撑，并用编号标注依据（`【1】`）；点击来源
  可跳转原文并高亮命中段落。
- **不足即拒答。** 检索相关性低于阈值时，固定回复 `知识库中没有找到相关内容`，绝不猜测。
- **敏感内容强制端侧。** 标记为敏感的笔记即使云端已开启也只走端侧引擎，并且在构造云端
  请求（含工具调用）之前就被过滤掉。
- **多格式导入。** PDF、DOC/DOCX、XLS/XLSX、CSV/TSV、Markdown 与纯文本，另有截图
  OCR（ML Kit 中文）与语音转写（sherpa-onnx）。
- **静态加密。** 可选用 SQLCipher 加密两个数据库、用 AES-GCM 加密留存的原始文件，
  并支持生物识别解锁。
- **知识图谱。** 基于检索所用的同一套嵌入，为笔记之间建立相似度关系图。

## 工作原理

### 导入

```
文件 / 文本 / 图片 / 音频
        │
        ├─ PDF           → PDFBox-Android
        ├─ DOC(X)/XLS(X) → Apache POI
        ├─ MD / TXT      → commonmark / 原文
        ├─ 图片          → ML Kit OCR（中文）
        └─ 音频          → sherpa-onnx（SenseVoice、流式 zipformer）
        │
        ▼
   按标题与句子边界分块
        │
        ├─► 嵌入（MediaPipe Gecko 256 INT8，768 维） → sqlite-vec
        └─► 关键词行                                  → SQLite FTS5（trigram）
```

### 检索

默识同时跑两条召回通道再融合：

1. **向量召回**：查 sqlite-vec 索引，并设最低相似度门槛。
2. **关键词召回**：查使用 trigram 分词器的 FTS5 表。中文子串匹配与精确词
   （标识符、数字、缩写）在嵌入表现较弱的地方由它兜住。

两条通道用 **RRF（倒数排名融合，`k = 60`）** 合并，再同时按绝对相关性阈值和相对
最高分的比例过滤。

还有两步用于保护答案质量：

- **查询扩展（伪相关反馈）**：首轮结果偏弱时，从最佳命中的标题与正文派生出补充查询
  再检索一次。不额外调用模型，简单问题也不会因此变慢。
- **单篇限量**：同一篇笔记只保留有限个分块，避免一篇长文档挤占整个上下文。

### 作答

命中的片段会带编号交给模型，模型必须分两段输出——一段分析、一段答案——并标注片段编号。
若片段不足以支撑回答，模型被要求逐字输出固定的拒答文案，客户端还会独立校验这个拒答。

对话记录、来源以及实际使用的依据都会落盘，因此事后可以回溯一条回答是如何得出的。

## 技术栈

| 领域 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.4.20、Jetpack Compose（Material 3） |
| 架构 | 多 Activity + 标签切换、MVVM、单向 `StateFlow` |
| 端侧推理 | LiteRT-LM（`litertlm-android` 0.17.1） |
| 嵌入 | MediaPipe Gecko 256 INT8（768 维），经 `google-localagents-rag` |
| 向量索引 | sqlite-vec（`SqliteVectorStore`） |
| 关键词索引 | SQLite FTS5（trigram 分词器） |
| 持久化 | Room 3（KSP）、SQLCipher 4.19.1 |
| 文档解析 | Apache POI 5.5.1、PDFBox-Android 2.0.27.0、commonmark 0.30.0 |
| OCR | ML Kit Text Recognition 16.0.1（中文） |
| 语音 | sherpa-onnx 1.13.7（SenseVoice + 流式 zipformer） |
| 云端（可选） | `openai-kotlin` 4.1.0，基于 Ktor 3.6.0 / OkHttp |
| 图片 | Coil 3.6.3 |
| 单元测试 | JUnit 5（Jupiter） |
| 构建 | AGP 9.4.1、Gradle 9.5.0、JDK 21、compileSdk/targetSdk 37、minSdk 26 |

## 架构

单 Gradle 模块（`:app`），分层保证 UI 不直接触碰 Room、文件与模型 API：

```
app/src/main/kotlin/dev/zlddba/moshiapp/
├── activities/   各页面的 Activity + Compose UI + ViewModel
├── domain/       检索、问答编排、工具、分块、安全、偏好
├── data/         Room 实体与 DAO、向量库、关键词索引、仓库
├── engine/       端侧与云端推理，置于同一套网关接口之后
├── ingest/       文档解析、OCR、语音、模型文件管理
└── ui/           设计令牌（颜色/字体/形状）与共用 UI 辅助
```

端侧与云端引擎共用一套抽象，模型与端点因此保持可替换。检索质量、提示词构造与拒答
判定由两个引擎共享。

## 快速开始

### 环境要求

- **JDK 21**
- **Android SDK 37**（compileSdk 与 targetSdk）
- Android Studio，AGP 9.4.1 或更新
- 一台 **arm64-v8a** 的**真机**，系统 **Android 8.0（API 26）或更新**

> 本应用只打包 `arm64-v8a`。32 位 ARM 与 x86 设备无法安装，多数 x86 模拟器也无法运行。

### 构建与运行

```bash
git clone git@github.com:zlddba/moshi.git
cd moshi
./gradlew :app:assembleDebug
```

或用 Android Studio 打开 `moshi/` 目录后直接 Run。首次同步会生成 `local.properties`
并写入 `sdk.dir`；该文件已被忽略，请勿提交。

### 关于 `repo/` 目录

`com.github.k2fsa:sherpa-onnx:1.13.7` 从 `repo/` 下的本地 Maven 镜像解析，而该目录
**刻意不纳入 Git 跟踪**（它是 47 MB 的二进制）。同一坐标也在 `settings.gradle.kts`
中声明了 JitPack 仓库，因此全新克隆通常可从 JitPack 解析成功。若你的网络无法访问
JitPack，请按相同的 Maven 目录结构把构件放回：

```
repo/com/github/k2fsa/sherpa-onnx/1.13.7/sherpa-onnx-1.13.7.aar
```

## 端侧模型

模型文件**不在本仓库内**。应用在需要时从 ModelScope 下载（备用源为 `hf-mirror.com`），
使用前会校验文件大小：

| 用途 | 模型 | 文件 |
| --- | --- | --- |
| 对话（默认） | Gemma 4 E2B | `gemma-4-E2B-it.litertlm` |
| 对话（低端设备） | Qwen3-0.6B | `qwen3_0_6b_mixed_int4.litertlm` |
| 嵌入 | Gecko 256 INT8 | `Gecko_256_quant.tflite` + `sentencepiece.model` |
| 语音（离线） | SenseVoice | `model.int8.onnx` + `tokens.txt` |
| 语音（流式） | Zipformer | `encoder/decoder/joiner-epoch-99-avg-1.int8.onnx` |

问答需要对话模型，语义检索需要嵌入模型。应用会检测设备内存并自动降级对话模型；
没有嵌入模型时，检索会退化为纯关键词匹配。

## 云端引擎（可选）

云端引擎**默认关闭**，必须由用户显式填写服务地址、密钥与模型名后启用。它兼容
OpenAI 接口，因此任何兼容端点都可以接入。

启用后离开设备的内容只有两项：

- 你的问题；
- 检索已经从你的知识库中选出的片段。

不会离开设备的包括：原始文件、嵌入向量，以及任何被标记为敏感的笔记——它们在构造云端
请求之前就被过滤，工具调用内部同样如此。

云端路径还支持函数调用，提供两个 MCP 形状的工具（`search_knowledge`、`get_note`），
但它们**在本地执行**，因此模型可以要求补充证据，而知识库本身不出端。失败时会向用户
明确说明具体原因（密钥无效、地址不可达、TLS 失败、超时、HTTP 状态码），而不是静默吞掉。

## 隐私模型

| | 端侧引擎 | 云端引擎 |
| --- | --- | --- |
| 原始文件 | 留在设备 | 留在设备 |
| 嵌入与索引 | 留在设备 | 留在设备 |
| 检索命中的片段 | 留在设备 | 会发送 |
| 问题 | 留在设备 | 会发送 |
| 标记为敏感的笔记 | 留在设备 | 永不发送 |

数据库可选用 SQLCipher 加密，留存的原始文件可用 AES-GCM 加密，解锁可置于生物识别之后。

## 分支模型

| 分支 | 用途 |
| --- | --- |
| `master` | 稳定可演示版本，只接受合并 |
| `develop` | 集成分支 |
| `feature/*` | 功能开发，从 `develop` 切出，以 `--no-ff` 合回 |

提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)，
subject 使用中文。

## 当前状态

默识是一个仍在推进的参赛项目。「导入 → 分块 → 嵌入 → 检索 → 带来源作答与拒答」这条
核心闭环已经实现并在真机上跑通。正在持续打磨的方向包括检索质量调优、知识图谱视图，
以及更广的设备覆盖。

## 许可证

以 [Apache License 2.0](../../LICENSE) 授权。

## 致谢

本项目为第十六届华北五省计算机应用大赛参赛作品。

站在这些项目之上：LiteRT-LM、MediaPipe / `google-localagents-rag`、sqlite-vec、
SQLCipher、Apache POI、PDFBox、ML Kit、sherpa-onnx、Room 与 Jetpack Compose。
