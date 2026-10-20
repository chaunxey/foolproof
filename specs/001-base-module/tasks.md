# Tasks: base 模块（架构基建）

**Input**: Design documents from `specs/001-base-module/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（用户故事与需求）

**Tests**: 本 feature **必须写测试**（spec FR-023 + 宪法 §「质量门槛」），每个用户故事的验收场景对应至少一条测试。

**Organization**: 按用户故事分组，每个故事可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可与同阶段其它 [P] 任务并行（不同文件、无依赖）
- **[Story]**：所属用户故事（US1–US4）
- 所有路径相对仓库根目录

## Path Conventions

多模块 Android 工程：`:base` 位于 `base/`，`:app` 位于 `app/`，路径按 plan.md 的结构。

---

## Phase 1: Setup（共享骨架）

**目的**：让 `:base` 模块存在、依赖可解析、能编译通过（此时还没有任何代码）

- [x] T001 在 `settings.gradle.kts` 中注册 `include(":base")`
- [x] T002 在 `gradle/libs.versions.toml` 中补齐：`android-library` 插件（`com.android.library`，version.ref=agp）、`kotlin-serialization` 插件（`org.jetbrains.kotlin.plugin.serialization`，version.ref=kotlin）、以及 ktor 3.6.0 / koin 4.2.2 / serialization 1.12.0 / coroutines 1.11.0 的版本与库条目
- [x] T003 创建 `base/build.gradle.kts`：应用 `android.library` + `kotlin.serialization` 插件，`namespace = "com.foolproof.base"`，`compileSdk { version = release(37) }`，`minSdk 24`，Java 11，依赖全部取自版本目录（不得硬编码版本）
- [x] T004 [P] 创建 `base/src/main/AndroidManifest.xml`：空 `<manifest/>`，**不声明任何权限**
- [x] T005 在 `base/build.gradle.kts` 中补充测试依赖：`junit`、`kotlinx-coroutines-test`、`ktor-client-mock`、`koin-test`（均取自版本目录）
- [x] T006 运行 `./gradlew :base:assembleDebug`，确认骨架与依赖无传递冲突（**失败则停下先解决版本问题，不进入后续阶段**）

---

## Phase 2: Foundational（被所有用户故事共同使用的契约类型）

**目的**：先定类型，再写行为，避免各故事互相等待

- [x] T007 [P] 创建 `base/src/main/java/com/foolproof/base/mvi/MviContract.kt`：`UiState`、`UiEffect`、`UiAction` 三个标记接口（对应 FR-007）
- [x] T008 [P] 创建 `base/src/main/java/com/foolproof/base/network/ApiResult.kt`：`Success<T>` / `Failure(NetworkError)`（对应 FR-013）
- [x] T009 [P] 创建 `base/src/main/java/com/foolproof/base/network/NetworkError.kt`：`NoNetwork` / `Timeout` / `Unauthorized` / `Http(code, body)` / `Parse` / `Unknown`（对应 FR-014）
- [x] T010 [P] 创建 `base/src/main/java/com/foolproof/base/network/HttpConfig.kt`：`baseUrl`、默认请求头、连接/请求超时、日志开关，并提供 `default()`（对应 FR-015）

**Checkpoint**：Phase 1 + 2 完成即可并行开展后面三个 P1 故事。

---

## Phase 3: US1 — 新增页面不再重复写状态样板 (P1)

**目标**：`BaseViewModel<S, E>` 可用，三条验收场景有测试覆盖

- [x] T011 [US1] 创建 `base/src/main/java/com/foolproof/base/mvi/BaseViewModel.kt`：`MutableStateFlow` private、`uiState: StateFlow<S>` 只读、`Channel<E>(BUFFERED)` + `receiveAsFlow()`、`dispatch(action)` 唯一入口、受保护的 `updateState {}` 与 `sendEffect()`（对应 FR-007~FR-011）
- [x] T012 [US1] 创建 `base/src/test/java/com/foolproof/base/mvi/BaseViewModelTest.kt`：用 `FakeViewModel` 覆盖验收场景 1（dispatch 更新状态）、2（两个收集者只收到一次事件）、3（重建后拿到最新状态），并断言无公开可变容器（对应 FR-008/FR-009/FR-011、FR-023）
- [x] T013 [US1] 运行 `./gradlew :base:testDebugUnitTest --tests "*BaseViewModelTest*"` 通过

---

## Phase 4: US2 — 一次请求拿到统一结果 (P1)

**目标**：对外只有 `get` / `post`，五类响应都映射为 `ApiResult`，不抛异常

- [x] T014 [US2] 创建 `base/src/main/java/com/foolproof/base/network/HttpClientFactory.kt`：构造单例 `HttpClient`（OkHttp 引擎），装配 `ContentNegotiation(Json)`、`DefaultRequest`（默认头 + baseUrl）、`HttpTimeout`、`Logging`（仅 debug 且开关打开时）
- [x] T015 [US2] 创建 `base/src/main/java/com/foolproof/base/network/ErrorMapper.kt`：把连接异常 / 超时 / 401 / 其它 4xx-5xx / 序列化失败 / 未分类异常映射为 `NetworkError`（对应 FR-014）
- [x] T016 [US2] 创建 `base/src/main/java/com/foolproof/base/network/NetworkClient.kt`：只暴露 `suspend fun <reified T> get(...)` 与 `post(...)`，内部统一 `safeCall` 返回 `ApiResult<T>`，支持 query/body/额外请求头（对应 FR-012/FR-013/FR-016）
- [x] T017 [US2] 创建 `base/src/test/java/com/foolproof/base/network/ErrorMapperTest.kt`：逐一断言六种映射（对应 FR-014）
- [x] T018 [US2] 创建 `base/src/test/java/com/foolproof/base/network/NetworkClientTest.kt`：用 `MockEngine` 覆盖 200 / 401 / 500 / 超时 / 非法 JSON 五类响应，并断言默认请求头被附加、`baseUrl` 拼接正确（对应 FR-012/FR-013/FR-015、SC-004）
- [x] T019 [US2] 运行 `./gradlew :base:testDebugUnitTest --tests "*Network*" --tests "*ErrorMapper*"` 通过

---

## Phase 5: US3 — 日志可定位行号且不泄密 (P1)

**目标**：`LogUtil` 单例可用、release 静默、敏感串脱敏、禁用时零开销

- [x] T020 [US3] 创建 `base/src/main/java/com/foolproof/base/log/LogUtil.kt`：`object` + 可替换 `sink` + `enabled`（默认 `BuildConfig.DEBUG`）、`d/i/w/e(message: () -> String)` 仅在启用时求值、从 `stackTrace` 定位首个非 `LogUtil` 帧取文件名与行号、敏感串脱敏（对应 FR-018~FR-021）
- [x] T021 [US3] 创建 `base/src/test/java/com/foolproof/base/log/LogUtilTest.kt`：断言输出格式含 `文件名:行号`、禁用时 sink 未被调用、`sk-` 密钥与 `Authorization` 头被脱敏（对应 FR-019/FR-020/FR-021）
- [x] T022 [US3] 在 `LogUtilTest` 中补充「禁用时消息 lambda 未被求值」的断言（计数变量），验证零开销设计（对应 SC-005 的实现前提）
- [x] T023 [US3] 运行 `./gradlew :base:testDebugUnitTest --tests "*LogUtilTest*"` 通过

---

## Phase 6: US4 — 应用启动后依赖随手可得 (P2)

**目标**：`baseModule` + `BaseApplication`，容器可校验、启动幂等

- [x] T024 [US4] 创建 `base/src/main/java/com/foolproof/base/di/BaseModule.kt`：注册 `HttpConfig`、`HttpClient`（单例）、`NetworkClient`（单例）（对应 FR-006/FR-016）
- [x] T025 [US4] 创建 `base/src/main/java/com/foolproof/base/BaseApplication.kt`：`open fun koinModules(): List<Module> = listOf(baseModule)`，`onCreate` 中幂等启动 Koin（已启动则跳过，对应 FR-004/FR-005）
- [x] T026 [US4] 创建 `base/src/test/java/com/foolproof/base/di/BaseModuleTest.kt`：逐类型 `resolve` 校验装配（不用 `verify()`，它会误报 `HttpClient.engine`），并断言重复启动不抛致命异常、未登记类型 resolve 抛可见异常（对应 US4 三条验收场景、FR-023）
- [x] T027 [US4] 运行 `./gradlew :base:testDebugUnitTest --tests "*BaseModuleTest*"` 通过

---

## Phase 7: Polish（集成与收口）

- [x] T028 在 `app/build.gradle.kts` 增加 `implementation(project(":base"))`（对应 FR-001）
- [x] T029 创建 `app/src/main/java/com/foolproof/FoolproofApp.kt`：`class FoolproofApp : BaseApplication()`，覆写 `koinModules()` 以便后续追加 app 级模块
- [x] T030 在 `app/src/main/AndroidManifest.xml` 的 `<application>` 上设置 `android:name=".FoolproofApp"`
- [x] T031 运行 `./gradlew :app:assembleDebug`，确认整工程构建通过（对应 FR-001、SC-006）
- [x] T032 运行 `./gradlew :app:assembleRelease`，确认 release **构建**通过（R8 不缺类）；运行时行为不在此验证，见 T038（对应 FR-024 的构建侧）
- [x] T033 全仓检索 `android.util.Log` 与 `println(`，确认生产代码 0 处（`LogUtil` 的默认 sink 为唯一例外，对应 FR-022、SC-003）
- [x] T034 全仓检索 `HttpClient(` / `OkHttpClient(` 构造，确认 `:base` 之外 0 处（对应 FR-006、SC-002）
- [x] T035 运行完整 `./gradlew :base:testDebugUnitTest`，确认全部测试通过（对应 FR-023）
- [x] T036 更新 `README.md`：模块结构加上 `base/`、技术栈表补上真实依赖版本与工具链版本
- [x] T037 更新宪法 §11 公共组件登记清单：补 `NetworkClient`、`BaseViewModel`、`BaseApplication` 的位置（宪法要求新公共组件登记）
- [ ] T038 真机 / 模拟器冒烟：安装 release 包，发起一次真实请求并观察无日志输出、混淆后反序列化正常（本机无设备，待执行）
- [x] T039 [US4] 新增 `base/src/test/java/com/foolproof/base/di/ViewModelRegistrationTest.kt`：验证 `viewModel { }` DSL 注册 `BaseViewModel` 子类后可解析，锁住注入范式（对应 FR-025）
- [x] T040 按负责人决定把 `LogUtil` 从 `object` 改为 Koin 注入的单例类，同步更新 `HttpClientFactory`、`BaseModule` 与全部测试（对应 FR-006/FR-018）
- [x] T041 新增 `base/consumer-rules.pro` 并在 `build.gradle.kts` 声明 `consumerProguardFiles`，覆盖 kotlinx.serialization 的 keep 规则；以 `app/build/outputs/mapping/release/configuration.txt` 确认规则已进入 R8（对应 FR-024）
- [x] T042 在 `NetworkClientTest` 补两条日志断言：logging 开启时 sink 收到内容、关闭时为空（对应 US2 验收场景 5、FR-020）

---

## Dependencies（阶段依赖）

```
Phase 1 (T001–T006)
   └─→ Phase 2 (T007–T010)  ←── T007/T008/T009/T010 互相独立，可并行
          ├─→ Phase 3 (US1: T011–T013)
          ├─→ Phase 4 (US2: T014–T019)
          ├─→ Phase 5 (US3: T020–T023)   ← 与 US1/US2 无依赖，可完全并行
          └─→ Phase 6 (US4: T024–T027)   ← 依赖 US2 的 HttpClient/NetworkClient
                 └─→ Phase 7 (T028–T037) ← 依赖全部前置
```

**关键约束**：

- T006 是**硬门槛**：依赖版本冲突必须在此解决，不能带着问题往下走
- T024 依赖 T016（`NetworkClient` 构造函数签名）与 T014（`HttpClientFactory`）
- T032 依赖 T028–T031（release 需要整个 app 能编译）

## 并行机会

| 阶段 | 可并行任务 | 说明 |
| --- | --- | --- |
| Phase 2 | T007 / T008 / T009 / T010 | 四个独立文件 |
| Phase 3 / 4 / 5 | 三个 P1 故事之间除 T024 外无依赖 | 若多人协作，可一人一个故事 |
| Phase 5 | T020 → T021 → T022 需顺序 | 同一文件内递进 |

## MVP 范围

**最小可用切片 = Phase 1 + 2 + Phase 3 + Phase 5**（T001–T023）：
此时已能「开新页面用 BaseViewModel、打日志带行号」，足以支撑第一个业务页面起步；
网络层（Phase 4）与 Koin 装配（Phase 6）可稍后补齐，但不补齐则无法满足宪法 §5 的注入约束。

## 需求覆盖自检

| 需求 | 覆盖任务 |
| --- | --- |
| FR-001 / FR-002 / FR-003（模块与依赖） | T001、T003、T004、T028、T031 |
| FR-004 / FR-005 / FR-006（Koin 与入口） | T024、T025、T026、T040 |
| FR-007 ~ FR-011（MVI） | T007、T011、T012、T013 |
| FR-012 ~ FR-015（网络） | T008、T009、T010、T014–T019 |
| FR-016 / FR-017（已合并至 FR-006 / FR-020） | 无独立任务；约束由 FR-006、FR-020 的任务覆盖 |
| FR-018 ~ FR-022（日志） | T020–T023、T033、T040、T042 |
| FR-023（测试） | T012、T017、T018、T021、T022、T026、T035、T039、T042 |
| FR-024（release keep） | T032（构建）、T041（规则文件） |
| FR-025（ViewModel 注入范式） | T039 |
| SC-002 / SC-003（零散落调用） | T033、T034 |
| SC-005（release 静默） | T022（开关逻辑）、T042（网络日志）、T038（运行时，待执行） |
| SC-001 / SC-006（跨 feature 验收） | 不在本 feature 内验证，移交首个业务 feature |
