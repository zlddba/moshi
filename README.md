# 默识（Moshi）

端侧优先的个人知识库问答 App：笔记与文档导入本机，分块、嵌入、检索、问答全部在设备本地闭环。第十六届华北五省计算机应用大赛参赛作品。

## 核心特性

- **本地零上传**：端侧模式不申请网络权限，解析、向量检索与回答生成不依赖云端。
- **双引擎**：端侧推理 LiteRT-LM（默认开箱即用），云端 OpenAI 兼容网关可选开启；仅发送「问题 + 检索命中的相关片段」，原始知识不出端。
- **有据才答**：所有回答强制附来源，可一键跳转原文并高亮；相关性不足时固定拒答「知识库中没有找到相关内容」，禁止编造。
- **敏感强制端侧**：标记为敏感的知识即使云端已开启也只走本地引擎。
- **多格式导入**：PDF、DOCX、Markdown、TXT、截图 OCR（ML Kit 中文）与语音转写，全部本机处理。

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Room · sqlite-vec/usearch · LiteRT-LM · MediaPipe Gecko 嵌入 · ML Kit · Coil 3 · MVVM（单向数据流 + StateFlow）

## 工程结构

单模块 `:app`，Kotlin 源码位于 `app/src/main/kotlin/`：

```
app/src/main/kotlin/dev/zlddba/moshiapp/
├── activities/   # 页面三件套（Activity + UI + ViewModel）：launch/firstLaunch/main 等
├── domain/       # 领域层
├── data/         # 数据层：Room、向量索引、仓库、偏好
├── engine/       # 本地/云端问答引擎抽象
├── ingest/       # 文档导入、OCR、转写
└── ui/theme/     # 质感简约设计语言（色板/字阶/形状令牌）
```

## 构建

- 环境：Android Studio（AGP 9.4.1 / Gradle 9.5.0 / JDK 21）、compileSdk 37、minSdk 24。
- 打开 `moshi/` 目录，首次同步会在 `local.properties` 写入 `sdk.dir`（已被忽略，勿提交）。
- 运行：选择 `app` 运行配置直接 Run；本仓库约定代码修改后不主动执行构建，由开发者自行 Sync 与验证。

## 分支模型

| 分支 | 用途 |
| ---- | ---- |
| `master` | 稳定可演示版本，只接受合并 |
| `develop` | 集成分支，功能在此汇聚 |
| `feature/*` | 功能开发，从 `develop` 切出、`--no-ff` 合回 |

提交信息遵循 Conventional Commits + 中文 subject，详见设计文档库内《Git 使用规范》。

## 当前状态

- ✅ 启动引导链（Launch → FirstLaunch 三屏 → 主框架）、十屏静态 UI 与质感简约主题。
- ⏳ P0 数据闭环：文档导入 → 分块 → 端侧嵌入 → 向量检索 → 问答 + 来源标注 + 拒答判定。
