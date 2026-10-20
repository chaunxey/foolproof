# Feature Specification: base 模块（架构基建）

**Feature Branch**: `feature_foundation1`（目录：`specs/001-base-module`）

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "搭建 base 模块：BaseApplication + Koin + MVI 基类 + Ktor 封装 + LogUtil"

## 概述

`base` 是 5 个业务功能的公共地基，必须**先于**任何业务功能落地。它提供四件事：

1. **应用入口**：`BaseApplication` 负责 Koin 容器启动；
2. **MVI 基类**：统一「状态用 `StateFlow`、副作用用 `Channel`」的 ViewModel 契约，消除各页面重复样板；
3. **网络出口**：Ktor `HttpClient` 统一封装，对外只暴露 `get` / `post`，错误统一映射，禁止业务层直接接触 Ktor；
4. **日志**：`LogUtil` 单例（由 Koin 提供），仅 debug 输出，带文件名与行号。

它**不含**任何 UI、导航、业务逻辑，也不依赖 `:app` / `:common` / 任何 `feature` 模块（宪法 §3）。

---

## User Scenarios & Testing

> 本 feature 的「用户」是后续开发 feature 模块的工程师。每个故事都可以用 JVM 单元测试独立验证。

### User Story 1 - 新增页面不再重复写状态样板 (Priority: P1)

开发者新建一个页面时，继承 `BaseViewModel<S, E>` 即可获得只读的 `StateFlow<S>` 与一次性事件流 `Flow<E>`，
只需实现 `dispatch(action)` 和状态更新逻辑，不用自己声明 `MutableStateFlow`、`Channel`、`receiveAsFlow`。

**Why this priority**: 5 个功能页面都要用；不先固化，后续每个页面都会长出一套略有差异的写法，与宪法 §4 冲突。

**Independent Test**: 在测试源码里写一个最小的 `FakeViewModel : BaseViewModel<FakeState, FakeEffect>`，
断言：① `uiState` 外部只读；② 调用 `dispatch` 后状态更新；③ `effect` 能收到事件且只被消费一次。

**Acceptance Scenarios**:

1. **Given** 一个继承 `BaseViewModel` 的 ViewModel，**When** UI 侧收集 `uiState` 并发送一个 action，**Then** 状态按实现更新，且外部无法调用 `_uiState` 之类的可变引用。
2. **Given** ViewModel 发出一次性事件，**When** 两个收集者同时收集 `effect`，**Then** 同一事件只被其中一个收到（`Channel` 语义），不会重复触发导航/弹窗。
3. **Given** 一个页面重建（回前台），**When** 重新收集 `uiState`，**Then** 拿到的是最新状态而非初始状态。

---

### User Story 2 - 一次请求拿到统一结果，而不是到处 try/catch (Priority: P1)

开发者调用 `base` 暴露的 `get` / `post`，得到 `ApiResult.Success(data)` 或 `ApiResult.Failure(NetworkError)`，
不需要自己处理超时、非 2xx、JSON 解析失败等分支，也不需要构造 `HttpClient`。

**Why this priority**: 唯一联网功能（语音聊天）依赖它；错误处理散落各处会直接违反宪法 §5 与 §9。

**Independent Test**: 用 Ktor `MockEngine` 构造 200 / 401 / 500 / 超时 / 非法 JSON 五种响应，
断言每种都被映射到预期的 `NetworkError` 子类型，且正常响应能反序列化为目标类型。

**Acceptance Scenarios**:

1. **Given** 服务端返回 200 + 合法 JSON，**When** 调用 `get<T>`，**Then** 返回 `Success` 且字段正确反序列化。
2. **Given** 服务端返回 401，**When** 调用任意请求，**Then** 返回 `Failure(Unauthorized)`，不抛异常。
3. **Given** 请求超时，**When** 调用任意请求，**Then** 返回 `Failure(Timeout)`，且不崩溃。
4. **Given** 默认请求头已配置，**When** 发出任何请求，**Then** 请求头被自动附加（由 MockEngine 断言）。
5. **Given** 未显式配置日志，**When** debug 构建发请求，**Then** 有请求/响应日志；**When** release 构建，**Then** 无任何输出。

---

### User Story 3 - 日志统一，可定位到行，且不会泄露密钥 (Priority: P1)

开发者注入 `LogUtil` 后调用 `logUtil.d/i/w/e { ... }`，输出形如 `MainActivity.kt:42 D xxx`；
release 包中完全不输出；
API Key 之类的敏感串在输出前被脱敏。

**Why this priority**: 宪法 §6 与 §9 的硬约束（release 留 `Log.d` 即一票否决）。

**Independent Test**: 注入一个可替换的输出 sink，断言：① 开启时收到包含文件名与行号的内容；② 关闭时不收到任何内容；③ 内容中 `sk-` 开头的密钥被脱敏。

**Acceptance Scenarios**:

1. **Given** debug 开关打开，**When** 调用 `logUtil.d { "hello" }`，**Then** 输出包含当前文件名、行号与 `hello`。
2. **Given** debug 开关关闭，**When** 调用任意日志方法，**Then** 输出 sink 收不到任何内容。
3. **Given** 日志内容包含 `sk-abcdef123456`，**When** 输出，**Then** 实际内容为脱敏形式（如 `sk-***3456`）。

---

### User Story 4 - 应用启动后依赖随手可得 (Priority: P2)

`BaseApplication.onCreate` 中完成 Koin 启动，任意位置 `get<HttpClient>()` 或注入 `BaseViewModel` 子类都能成功，
测试时可替换为假实现。

**Why this priority**: 是 US1–US3 的装配前提，但本身逻辑极薄。

**Independent Test**: 启动 Koin 后 resolve 网络客户端与一个 ViewModel，断言非空；再次启动不抛异常。

**Acceptance Scenarios**:

1. **Given** 应用以 `BaseApplication` 启动，**When** resolve 已登记的类型，**Then** 返回实例。
2. **Given** Koin 已启动，**When** 再次启动（如测试环境重复初始化），**Then** 不抛致命异常。
3. **Given** 某类型未登记，**When** resolve，**Then** 抛出可识别的 Koin 异常（而非静默返回 null）。

---

### 边界情况

- **Koin 未初始化就 resolve**：必须抛出明确异常，而不是返回半初始化对象。
- **Channel 事件堆积**：使用缓冲策略（`Channel.BUFFERED`），事件不丢弃、不阻塞发送方。
- **网络层并发**：客户端全局单例，多协程并发调用不泄漏连接（复用 engine）。
- **release 混淆**：Ktor + kotlinx.serialization 在 R8 下仍能正常工作（需要 keep 规则）。
- **空 / 超大响应体**：超时与大小限制有默认值，且可配置。
- **HTTP 非 JSON 响应**（如网关返回 HTML 错误页）：不得崩溃，映射为解析失败或 HTTP 错误。

---

## Requirements

### Functional Requirements

**模块与依赖**

- **FR-001**: 系统 MUST 提供名为 `base` 的 Android Library 模块（`settings.gradle.kts` 中 `:base`），可被 `:app` 依赖并成功编译。
- **FR-002**: `:base` MUST NOT 依赖 `:app`、`:common` 或任何 `:features:*` 模块（依赖单向，宪法 §3）。
- **FR-003**: `:base` MUST NOT 包含任何 UI（Activity、Compose 页面、导航），也不得申请 Android 权限。

**应用入口与依赖注入**

- **FR-004**: 系统 MUST 提供 `BaseApplication`，在 `onCreate` 中启动 Koin 并装配 `base` 自带的模块。
- **FR-005**: Koin 启动 MUST 幂等——重复调用不抛致命异常（便于测试与多入口场景）。
- **FR-006**: 网络客户端、`LogUtil`、ViewModel MUST 经 Koin 提供，业务代码通过注入获取而非自行 `new`；
  网络客户端 MUST 在全应用内唯一（`single`），业务模块不得自行创建。
  <!-- 2026-10-20 修订：原 FR-016「客户端唯一实例」已并入本条，不再单列。 -->

**MVI 基类**

- **FR-007**: 系统 MUST 提供泛型基类 `BaseViewModel<S, E>`：`S` 为不可变状态、`E` 为一次性事件。
- **FR-008**: `uiState` MUST 以只读 `StateFlow<S>` 对外暴露；可变容器 `MutableStateFlow` MUST 为 private。
- **FR-009**: 一次性事件 MUST 通过 `Channel<E>` 发送，并以 `Flow<E>` 对外暴露；事件 MUST NOT 进入状态对象。
- **FR-010**: 状态变更 MUST 只经由单一入口 `dispatch(action)`（或等价单向入口）触发，UI 不得直接改状态。
- **FR-011**: 同一事件 MUST 只被消费一次（两个收集者不会同时收到）。

**网络封装**

- **FR-012**: 系统 MUST 对外只提供 `get` 与 `post` 两类请求方法，支持 query 参数、请求体与附加请求头。
- **FR-013**: 所有请求 MUST 返回统一结果类型（成功携带反序列化数据、失败携带结构化错误），MUST NOT 向调用方抛 IO 异常。
- **FR-014**: 网络错误 MUST 映射为可区分类型，至少覆盖：无网络、超时、未授权（401）、客户端错误（4xx）、服务端错误（5xx）、响应解析失败。
- **FR-015**: 网络层 MUST 支持配置：基础地址、默认请求头、连接/请求超时、日志开关。
- **FR-016**（已并入 FR-006）: 保留编号仅为历史追溯，无独立约束。
- **FR-017**（已并入 FR-020）: 保留编号仅为历史追溯，无独立约束。

**日志**

- **FR-018**: 系统 MUST 提供日志工具 `LogUtil`，由 Koin 以单例（`single`）提供，业务侧注入使用；
  其输出实现 MUST 可替换（便于测试）。
  <!-- 2026-10-20 修订：原为 `object` 全局单例，按负责人决定改为 Koin 注入的类，
       使 FR-006「依赖经 Koin 提供」与本节不再冲突。 -->
- **FR-019**: 日志输出 MUST 包含：调用处文件名、行号、消息。
- **FR-020**: 日志 MUST 仅在 debug 构建输出；release 构建 MUST 静默——包括网络请求/响应日志
  与请求头中的凭据（本条吸收原 FR-017）。
- **FR-021**: 日志 MUST 对敏感串（`sk-` 开头的密钥、`Authorization` 头）做脱敏。
- **FR-022**: `:base` 内生产代码 MUST 不使用 `android.util.Log`、`println` 或第三方日志库（review + 静态检查保证）。

**质量**

- **FR-023**: `:base` MUST 为 US1–US4 的验收场景提供自动化测试，`./gradlew :base:testDebugUnitTest` 通过。
- **FR-024**: release 构建 MUST 具备 Ktor / kotlinx.serialization 的 keep 规则，保证混淆后网络与序列化可用；
  规则以 `base/consumer-rules.pro` 形式随 AAR 提供给消费方。
- **FR-025**: ViewModel MUST 通过 Koin 的 `viewModel { }` DSL 注册后注入（对应宪法 §5），
  `:base` MUST 提供可验证的注册范式（见 `ViewModelRegistrationTest`）。

### Key Entities

- **UiState**：不可变状态快照（`data class`），具体类型由各页面定义。
- **UiEffect**：一次性事件（`sealed interface`），导航 / 弹窗 / Toast 等。
- **Action**：UI 发往 ViewModel 的意图。
- **ApiResult\<T\>**：请求结果，成功携带 `T`、失败携带 `NetworkError`。
- **NetworkError**：错误分类（无网络 / 超时 / 未授权 / 4xx / 5xx / 解析失败）。
- **HttpConfig**：基础地址、默认头、超时、日志开关。

---

## Success Criteria

### Measurable Outcomes

- **SC-001**（跨 feature 验收）: 新增一个带副作用的页面，只需继承 `BaseViewModel` 并实现状态与 action 处理，
  **不再出现** `MutableStateFlow`、`Channel`、`receiveAsFlow` 的重复声明（不含业务逻辑的新页面 ViewModel 少于 40 行）。
  需真实页面才能度量，因此移到首个业务 feature 的验收中执行。
- **SC-002**: 全工程搜索 `OkHttpClient(` / `HttpClient(` 的构造调用，**`:base` 之外出现 0 处**。
- **SC-003**: 全工程搜索 `android.util.Log` 与 `println(`，生产代码 **0 处**（`LogUtil` 的默认 sink 为唯一例外）。
- **SC-004**: `MockEngine` 覆盖的 5 类响应（200 / 401 / 500 / 超时 / 非法 JSON）**全部**映射到预期结果，测试全绿。
- **SC-005**: release 构建产物运行时不产生任何日志输出（以注入 sink 的 release 变体验证）。
- **SC-006**（跨 feature 验收）: 下游接入成本可计数——新增一个 feature 模块并跑通一次 GET，
  改动文件 ≤ 4 个（模块构建脚本、`settings.gradle.kts` 注册、service 定义、调用点），无需修改 `:base`。
  需下游模块才能验证，移到首个业务 feature 验收。

---

## 假设

- **包名**：沿用 `com.foolproof`，`base` 模块包名 `com.foolproof.base`（2026-10-20 已由负责人拍板，宪法 §10-2 同步标记为已决）。
- **Ktor 引擎**：默认使用 OkHttp 引擎（与 Android 生态及后续 Coil 共存，不引入第二套网络栈）。
- **序列化**：`kotlinx.serialization` + `ContentNegotiation`。
- **范围外**：导航封装、API service 定义（属 `:common`）、Room、EncryptedSharedPreferences、图片处理、任何业务逻辑与页面。
- **minSdk**：24，与宪法一致。
- **测试环境**：以 JVM 单元测试为主（`MockEngine` + `koin-test`），不依赖真机；`BaseApplication` 行为可用 Robolectric 或 androidTest 验证。
