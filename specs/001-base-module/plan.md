# Implementation Plan: base 模块（架构基建）

**Branch**: `feature_foundation1` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-base-module/spec.md`

## Summary

新建 Android Library `:base`，为后续 5 个业务功能提供四件地基：`BaseApplication`（Koin 启动）、
`BaseViewModel<S, E>`（`StateFlow` 状态 + `Channel` 副作用）、Ktor 网络封装（只暴露 `get`/`post`，
错误统一映射为 `ApiResult`）、`LogUtil`（debug-only、带文件名行号、敏感串脱敏）。

技术上无业务逻辑、无 UI、不依赖 `:app`/`:common`/feature 模块。真正的难点不在代码量，而在
**依赖版本必须与工程现有的 Kotlin 2.2.10 对齐**（见 Research）。

---

## Technical Context

**Language/Version**: Kotlin 2.2.10（由 AGP 9.4.1 内置编译），JVM Target 11

**Primary Dependencies**（版本已逐个核对 Maven Central POM，见 Research）

| 库 | 版本 | 用途 |
| --- | --- | --- |
| io.ktor:ktor-client-core / -okhttp / -content-negotiation / -logging | 3.3.3 | 网络客户端与序列化协商 |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.9.0 | JSON 序列化 |
| io.insert-koin:koin-android / koin-test | 4.1.1 | 依赖注入 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android / -test | 1.10.2 | 协程（与 Ktor 3.3.3 对齐） |
| io.ktor:ktor-client-mock | 3.3.3（test） | 网络层单元测试 |
| junit | 4.13.2（已有） | 单元测试 |

**Storage**: N/A（`base` 不持有任何持久化数据；API Key 等存储属 `:common`）

**Testing**: JUnit4 + `MockEngine`（网络）+ `koin-test` 的 `verify()`（容器装配）+ `kotlinx-coroutines-test`

**Target Platform**: Android，`minSdk 24`，`compileSdk/targetSdk 37`

**Project Type**: 多模块 Android 应用中的**基础库模块**

**Performance Goals**: 网络请求开销可忽略（薄封装）；`LogUtil` 关闭时必须是**零分配零 IO**（release 不产生字符串拼接）

**Constraints**:
- 依赖版本上限由 Kotlin 2.2.10 决定：Ktor 不得高于 3.3.x、serialization 不得高于 1.9.x、Koin 不得高于 4.1.x
- `:base` 不得引入 Compose（UI 依赖不该下沉到库模块），也不申请任何 Android 权限
- release 构建中日志与网络日志必须完全静默

**Scale/Scope**: 约 10 个生产类 + 8 个测试类；不涉及页面

---

## Constitution Check

*GATE：Phase 0 前置检查，Phase 1 设计后复核*

| 宪法条款 | 检查点 | 结论 |
| --- | --- | --- |
| §1 纯本地优先 | `base` 只提供网络**能力**，不发起任何请求、无权限声明、无默认远端地址 | ✅ 通过 |
| §2 技术栈锁定 | Ktor / Koin / kotlinx.serialization / 协程 / Compose 均在白名单内；**未引入** Turbine、Retrofit、Hilt、RxJava | ✅ 通过 |
| §3 模块边界 | `:base` 依赖方向为「仅第三方库」；不依赖 `:app`/`:common`/features；不含 UI | ✅ 通过 |
| §3 职责 | `BaseApplication`、`BaseViewModel<S,E>`、Ktor 封装、`LogUtil` 四项职责与宪法列出的完全一致 | ✅ 通过 |
| §4 MVI 规范 | `StateFlow` 持状态、`Channel` 发副作用、`dispatch(action)` 单向入口、`Buffer` 策略 | ✅ 通过 |
| §5 克制的注入 | 仅注入：网络客户端（需外部配置 + 单例）、`LogUtil`（需配置 debug 开关）、ViewModel（便于测试） | ✅ 通过 |
| §6 日志 | `object LogUtil`，debug-only，含文件名行号，禁 `android.util.Log` | ✅ 通过 |
| §7 YAGNI | 不做导航、不做 Room、不做 API service、不做拦截器插件体系 | ✅ 通过 |
| §9 一票否决项 | 无 feature 直连网络、无明文密钥、无 ViewModel 内 `startActivity` | ✅ 通过 |
| 质量门槛 | US1–US4 每条验收场景都有对应测试，`:base:testDebugUnitTest` 为完成标准 | ✅ 通过 |

**结论：无违反项，`Complexity Tracking` 留空。**

> 唯一需要向负责人明示的技术约束：**不能用各库的最新版**。这不是宪法冲突，而是工程现有
> Kotlin 版本（AGP 9 内置 2.2.10）对二进制兼容性的硬限制，取舍理由见 Research。

---

## Project Structure

### Documentation (this feature)

```text
specs/001-base-module/
├── spec.md          # 已完成
├── plan.md          # 本文件
└── tasks.md         # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
settings.gradle.kts           # + include(":base")
gradle/libs.versions.toml     # + ktor / koin / serialization / coroutines 版本与库定义

base/
├── build.gradle.kts          # Android Library，namespace = com.foolproof.base
└── src/
    ├── main/
    │   ├── AndroidManifest.xml           # 空 <manifest/>，无权限
    │   └── java/com/foolproof/base/
    │       ├── BaseApplication.kt        # Koin 启动（幂等）
    │       ├── di/BaseModule.kt          # baseModule：HttpConfig / HttpClient / NetworkClient
    │       ├── mvi/MviContract.kt        # UiState / UiEffect / UiAction 标记接口
    │       ├── mvi/BaseViewModel.kt      # BaseViewModel<S, E>
    │       ├── network/ApiResult.kt      # Success / Failure
    │       ├── network/NetworkError.kt   # sealed：NoNetwork/Timeout/Unauthorized/Http/Parse/Unknown
    │       ├── network/HttpConfig.kt     # baseUrl、默认头、超时、日志开关
    │       ├── network/HttpClientFactory.kt
    │       └── network/NetworkClient.kt  # 对外仅 get() / post()
    │       └── log/LogUtil.kt            # object，含 sink 注入与脱敏
    └── test/java/com/foolproof/base/
        ├── mvi/BaseViewModelTest.kt
        ├── network/NetworkClientTest.kt  # MockEngine 五类响应
        ├── network/ErrorMappingTest.kt
        └── log/LogUtilTest.kt

app/
├── build.gradle.kts          # + implementation(project(":base"))
└── src/main/
    ├── AndroidManifest.xml   # android:name 指向 :app 的 Application
    └── java/com/foolproof/FoolproofApp.kt   # 继承 BaseApplication，仅装配入口
```

`:app` 的改动**只做集成 plumbing**（一个 Application 子类 + 一行依赖），不加任何 UI 或业务。

**Structure Decision**: 采用宪法 §3 的「平铺模块」结构，`base/` 与会话根目录同级（与 `app/` 并列），
不使用 `features/` 目录（该目录留给 `:features:*`）。

---

## Research（Phase 0 结论）

### R1. 依赖版本必须回退，不能取最新

逐个抓取 Maven Central 的 POM，读取各库声明的 `kotlin-stdlib` 版本（等价于其编译期 Kotlin）：

| 库 | 最新版 | 其 stdlib | 可用版本 | 其 stdlib |
| --- | --- | --- | --- | --- |
| Ktor | 3.6.0 | **2.3.21** ❌ | **3.3.3** | 2.2.21 ✅ |
| kotlinx-serialization-json | 1.12.0 | **2.4.10** ❌ | **1.9.0** | 2.2.0 ✅ |
| Koin | 4.2.2 | **2.3.20** ❌ | **4.1.1** | 2.1.21 ✅ |

**结论**：工程 Kotlin 为 2.2.10，Kotlin 编译器不能读取比自身更新的 metadata，因此三个库都取
「最后一批用 2.2.x 编译的稳定版」。这三个版本同时互相兼容（Ktor 3.3.3 依赖 coroutines 1.10.2）。

**后续升级路径**：等 AGP 9 内置 Kotlin 升到 2.3+ 后，再统一升 Ktor 3.6 / serialization 1.12 / Koin 4.2。
升级属于宪法 §2 的「版本变更」，需负责人确认，并在 `libs.versions.toml` 一处改完。

### R2. 测试栈不引入新框架

需要验证「事件只消费一次」「release 静默」「五类错误映射」，业界常用 Turbine，
但 Turbine 不在宪法 §2 白名单。**改用协程原语**（`runTest` + 手动 `launch { collect }` + `advanceUntilIdle`），
零新增依赖。`kotlinx-coroutines-test` 与 `koin-test` 属已有库的测试构件，不构成新框架。

### R3. `BaseApplication` 的可测试性

不引入 Robolectric。做法：把「要装哪些 Koin module」抽成 `open fun modules(): List<Module>`，
`onCreate` 只负责 `startKoin`。测试在 JVM 上直接 `startKoin { modules(baseModule) }` +
`verify()` 校验装配，不需要 Android 运行时。

### R4. release 静默的实现方式

`LogUtil` 内部维护 `enabled: Boolean = BuildConfig.DEBUG` 与可替换 `sink: (String) -> Unit`。
方法体首行即 `if (!enabled) return`，避免任何字符串拼接与堆栈采集开销。
R8 在 release 下可直接裁掉整个方法体。

### R5. 调用方定位（文件名 + 行号）

不引入第三方日志库。取 `Thread.currentThread().stackTrace`，跳过 `LogUtil` 自身的栈帧，
定位第一个非 `LogUtil` 的帧，取其 `fileName` 与 `lineNumber`。
单测中通过 sink 捕获断言格式 `Foo.kt:42 → msg`。

### R6. R8 与序列化

`kotlinx.serialization` 由插件在编译期生成 serializer，运行时依赖反射较少；
Ktor 自带 consumer R8 规则。落地时以一次 `assembleRelease` + 手动发一次请求验证，
若被裁掉再在 `:base` 的 consumer 规则里补 `-keep`（AGP 9 下位置为 `src/main/keepRules/`）。

---

## Design Decisions（Phase 1）

### D1. MVI 契约

```kotlin
interface UiState           // 标记：不可变 data class
interface UiEffect          // 标记：sealed，一次性事件
interface UiAction          // 标记：UI 发往 VM 的意图

abstract class BaseViewModel<S : UiState, E : UiEffect> : ViewModel() {
    private val _uiState: MutableStateFlow<S>      // 子类通过 initialState 提供
    val uiState: StateFlow<S>
    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect: Flow<E> = _effect.receiveAsFlow()
    fun dispatch(action: UiAction)                 // 子类实现 onAction
    protected fun sendEffect(effect: E)
}
```

- `uiState` 只读；`_uiState` private，子类只能通过受保护的 `updateState { }` 修改
- `effect` 用 `receiveAsFlow()`，多个收集者公平竞争，保证「同一事件只被消费一次」（FR-011）
- `dispatch` 为唯一入口（FR-010）；`onAction` 为抽象方法

### D2. 网络层分层

```
NetworkClient（对外门面，仅 get/post）
   → HttpClient（Ktor，单例，由 Koin 提供）
       → ContentNegotiation(json) / DefaultRequest(headers) / HttpTimeout / Logging(条件启用)
   → 统一包装 safeCall { } → ApiResult<T>
```

- `ApiResult<T>`：`Success(data)` / `Failure(error: NetworkError)`
- `NetworkError`：`NoNetwork` / `Timeout` / `Unauthorized` / `Http(code, body)` / `Parse` / `Unknown(cause)`
- 映射顺序：连接异常→`NoNetwork`；`HttpRequestTimeoutException`→`Timeout`；401→`Unauthorized`；
  其它 4xx/5xx→`Http`；序列化异常→`Parse`；其余→`Unknown`
- `NetworkClient` **不抛异常**（FR-013），统一 `runCatching` + 映射
- 泛型 `get<T>` / `post<T>` 依赖 `reified` + `kotlinx.serialization` 的 `typeInfo` 传递

### D3. 日志

```kotlin
object LogUtil {
    var sink: (String) -> Unit = { android.util.Log.d(TAG, it) }   // 唯一允许用 Log 的地方
    var enabled: Boolean = BuildConfig.DEBUG
    fun d(message: () -> String)   // lambda：未启用时不求值，避免字符串拼接
}
```

- 传 lambda 而非 String，是 FR「release 零开销」的实现手段
- 脱敏：正则替换 `sk-[A-Za-z0-9_-]{8,}` → `sk-***`；`Authorization: xxx` → `Authorization: ***`

### D4. Koin 装配

```kotlin
val baseModule = module {
    single { HttpConfig.default() }
    single { createHttpClient(get()) }      // HttpClient 单例
    single { NetworkClient(get()) }
}
```

- `BaseApplication` 暴露 `open fun koinModules(): List<Module> = listOf(baseModule)`，
  `:app` 的 `FoolproofApp` 覆写以追加自己的模块
- 幂等：`onCreate` 中 `if (GlobalContext.getOrNull() == null) startKoin { ... }`（FR-005）

### D5. 版本目录

在 `gradle/libs.versions.toml` 增加 `[versions]` 三元组：
`ktor = "3.3.3"`、`koin = "4.1.1"`、`serialization = "1.9.0"`、`coroutines = "1.10.2"`，
以及 libraries 条目；`:base` 与 `:app` 都从目录取版本（宪法 §2：禁止硬编码）。

---

## 实施顺序（供 /speckit-tasks 拆解）

1. **工程骨架**：`settings.gradle.kts` 加 `:base`、新建 `base/build.gradle.kts` 与空 Manifest、依赖目录加版本
2. **日志**：`LogUtil` + 测试（不依赖网络与 DI，最独立）
3. **MVI**：`MviContract` + `BaseViewModel` + 测试
4. **网络**：`ApiResult` / `NetworkError` / `HttpConfig` / `HttpClientFactory` / `NetworkClient` + MockEngine 测试
5. **DI 与入口**：`baseModule` + `BaseApplication` + Koin verify 测试
6. **集成**：`:app` 依赖 `:base`、`FoolproofApp` 继承、Manifest 指向、`assembleDebug` 通过
7. **收尾**：release 构建验证（日志静默 + R8 下序列化可用）、`ktlint`/review 检查 `android.util.Log` 零使用

## Complexity Tracking

无违反项，留空。

---

## 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 依赖版本回退后与 Compose BOM / AGP 9 产生传递冲突 | 编译失败 | 先做最小依赖引入（步骤 1）并 `assembleDebug` 验证，再写代码 |
| Ktor 3.3.3 与 minSdk 24 的 API 兼容（OkHttp 引擎版本） | 运行时 `NoSuchMethodError` | OkHttp 由 Ktor 传递引入，构建后检查 `minSdk` lint 报告 |
| `NetworkClient` 泛型 + 序列化在 R8 后被裁 | 线上崩溃 | 步骤 7 的 release 冒烟验证，必要时补 keep 规则 |
| `LogUtil` 栈帧定位在不同混淆级别下失效 | 行号错 | release 关闭日志，天然规避；debug 不混淆 |
