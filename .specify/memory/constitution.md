# Foolproof 项目宪法

**Foolproof** 是一个**纯本地**的图片 / 文件处理工具 App。本宪法规定项目不可协商的原则与约束，
所有规格（`specs/`）、计划（`plan.md`）、任务（`tasks.md`）与代码实现都必须符合本文件。

---

## 核心原则

### I. 纯本地优先（NON-NEGOTIABLE）

用户数据默认不离开设备。图片压缩、重复照片清理、PDF 生成与文件转换**必须完全在本地完成**，
不得为此引入任何云服务、上传通道或后端依赖。

唯一允许联网的场景是第 5 项功能「语音聊天」，且必须满足：

- 接口地址与 API Key 由**用户自行填写并保存在本地**，不得硬编码、不得内置作者的密钥；
- 联网行为必须由用户操作显式触发，禁止后台静默上报；
- 除该功能外，App 不得有其他网络出口。

**不做**：账号体系、云端同步、用户行为埋点与统计上报、远程配置、在线素材库。

### II. 技术栈锁定（NON-NEGOTIABLE）

技术栈由项目负责人确定，**AI 不得自作主张替换、升级或引入同类替代品**。包括但不限于：
不得用其他图片压缩库替代约定的方案，不得用 Hilt / Koin 之外的方式替代依赖注入，
不得引入 RxJava 与协程 Flow 混用替代约定的状态方案。

- 需要新增任何第三方依赖时，必须说明用途、体积影响与替代方案，**经项目负责人确认后**才能加入；
- 库版本统一在 `gradle/libs.versions.toml` 中声明，禁止在 `build.gradle.kts` 里硬编码版本号；
- 已有约定库的版本升级同样视为技术栈变更，需经确认。

### III. 模块边界与单向依赖

工程采用 `app` → `base` / `conmon` 的**单向依赖**结构：

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| `:base` | 架构基建：MVI 封装、`BaseApplication`、Koin、Retrofit/HttpClient 封装、日志 | 仅第三方库，不依赖 `:conmon`、`:app` |
| `:conmon` | 通用业务能力：图片压缩等可复用的能力封装 | 可依赖 `:base` |
| `:app` | 功能编排、UI、导航 | 可依赖 `:base`、`:conmon` |

- `:base` 与 `:conmon` **不得依赖 `:app`**，也不得包含 Android UI（Compose 页面、Activity）；
- 两个模块必须能被独立单元测试，不依赖 `:app` 的运行时环境；
- 功能逻辑不得直接堆在 `:app`，可复用的处理能力必须下沉到 `:conmon`。

> 待确认：`conmon` 为需求原文写法，若实为 `common` 的笔误，应在写第一行业务代码前改名——
> 模块名一旦被引用，改名成本随代码量上升。

### IV. 单向数据流（MVI）

`base` 模块统一封装 MVI 架构，所有 ViewModel 遵循同一套状态与副作用约定：

- **状态**：用 `StateFlow<UiState>` 暴露，`UiState` 必须是不可变的 `data class`，
  由 ViewModel 内部 `MutableStateFlow` 唯一持有，外部只读；
- **副作用**：一次性事件（导航、Toast、弹窗、权限请求）用 `Channel` 发送，
  由 UI 侧以 `receiveAsFlow()` 收集，**不得**塞进 `UiState` 里用布尔字段模拟事件；
- **事件入口**：UI 只向 ViewModel 发送意图（Intent/Action），不直接调用业务实现；
- **线程**：耗时工作（图片解码、PDF 写入、文件 IO）不得在主线程执行，统一在 ViewModel 或
  数据层切换到 IO 调度器。

### V. 克制的依赖注入（Koin）

使用 `BaseApplication` 完成 Koin 的初始化与模块装配。**只注入满足以下至少一条的类**：

- 需要外部提供（Context、配置、API Key 等运行时参数）；
- 实现容易被替换（如网络客户端、图片处理器）；
- 需要统一生命周期（单例、与页面或进程绑定）；
- 需要单例或多例的实例数量控制；
- 需要便于单元测试替换（如 ViewModel、Repository）。

不满足上述条件的普通工具类、无状态纯函数、数据模型**不要**注册进 Koin——
容器里塞满琐碎对象会让依赖关系变得不可读。

### VI. 可观测性最小化

统一的日志工具为 `object LogUtil`（全局单例，位于 `:base`）：

- **仅在 debug 构建下输出**，release 构建必须静默；
- 输出内容必须包含：**调用处文件名**、**行号**、日志信息；
- 生产代码中禁止直接使用 `android.util.Log`、`println` 或第三方日志库；
- 日志不得输出用户隐私内容（完整文件路径、图片内容、API Key、聊天正文）。

### VII. 简洁优先（YAGNI）

优先交付能工作的最小实现。不为「将来可能需要」预埋抽象层、接口、配置项或开关；
不为单一实现编写多余的 Strategy / Factory。重复出现三次以上再抽取。

---

## 工程约束

### 技术栈基线

- Kotlin + Jetpack Compose（Material 3），单 Activity 架构；
- `minSdk 24`，`compileSdk / targetSdk 37`；
- AGP 9 的写法约定（不要引入 `kotlin-android` 插件、`compileSdk` 块式写法、
  R8 规则放 `app/src/main/keepRules/`）见 [README](../README.md)；
- 网络层统一封装：Retrofit + HttpClient 封装，对外提供 GET / POST，支持可配置的
  日志、请求头与全局错误处理，**不允许**在业务代码里自行创建 `OkHttpClient` 或散落 Retrofit 实例；
- 图片压缩统一使用 [Compressor](https://github.com/zetbaitsu/Compressor)（`zetbaitsu/Compressor`），
  不得替换为其他压缩实现；封装在 `:conmon` 中对外暴露。

### 质量门槛

- 架构基建（`:base` 的 MVI 基类、Koin、网络封装、`LogUtil`）与 `:conmon` 的能力封装
  **必须有单元测试**；
- 提交前 `./gradlew :app:testDebugUnitTest` 必须通过；
- 涉及图片 / PDF 的核心算法（汉明距离比对、合并排序、格式转换）测试优先于实现；
- 提交信息遵循 Conventional Commits，分支模型 `main` + `feature/<name>`。

---

## 治理

1. 本宪法**优先于**任何其他开发实践、外部教程与 AI 的默认习惯；冲突时以本文件为准。
2. 违反「纯本地优先」或「技术栈锁定」的实现**一律退回**，不因工期压力放宽。
3. 修订须：说明动机 → 更新本文件 → 递增版本号 → 同步受影响的 `specs/` 与 `tasks.md`。
4. 版本号遵循语义化：MAJOR（原则增删或破坏性变更）、MINOR（新增原则 / 章节）、PATCH（措辞澄清）。
5. 若某条原则确实需要例外，例外必须**显式记录在本文件中**（附原因与范围），而不是在代码里悄悄绕过。

**Version**: 1.0.0 | **Ratified**: 2026-10-20 | **Last Amended**: 2026-10-20
