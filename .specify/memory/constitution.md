# 项目宪法（Project Constitution）

> 本文档是本项目的最高约束。任何代码、评审、重构都不得违反；如需修改，必须显式更新本文档并说明理由。
技术栈一旦锁定，禁止替换；新增依赖必须在第 2 节登记。

***

## 1. 项目定位：做什么 / 不做什么

### 1.1 做什么

纯本地工具 App，5 个功能：

1. 图片压缩（Compressor SDK，输出 WebP/JPEG）
2. 重复照片清理（dHash/pHash + 汉明距离分组）
3. 多张图片合并生成 PDF
4. 本地文件转 PDF
5. 类豆包语音聊天（用户自填 OpenAI API Key，回合制：Whisper → GPT-4o-mini → TTS）

### 1.2 不做什么（红线，本版不实现）

- ❌ 不写后端服务、不买服务器、不做用户账号体系
- ❌ 不做云同步、不做相册备份、不做社交分享
- ❌ 不内置 OpenAI Key，不代理、不转卖 API 调用
- ❌ 不做通用 AI 聊天机器人（仅限语音问答形态）
- ❌ 不做视频处理、不做云盘、不做笔记类功能

***

## 2. 技术栈锁定（禁止替换）

| 类别 | 选型 | 备注 |
| --- | --- | --- |
| 语言 | Kotlin | 禁止新增 Java 业务代码 |
| DI | **Koin** | 不用 Hilt/Dagger |
| 架构 | MVI（StateFlow + Channel） | 状态集中，副作用走 Channel |
| 网络 | **Ktor Client** | 不用 Retrofit；统一封装在 base |
| 序列化 | kotlinx.serialization | Ktor 官方配套 |
| 导航 | **Jetpack Navigation 3** | 封装在 common，禁止业务模块自己写 NavHost |
| 图片压缩 | **Compressor** (zetbaitsu) | https://github.com/zetbaitsu/Compressor |
| 图片加载 | **Coil 3** | 业务模块禁止直接 new ImageLoader |
| 本地存储 | **Room** + **EncryptedSharedPreferences** | 会话历史用 Room；API Key 必须用 EncryptedSP |
| 语音端点 | Silero VAD（ONNX，本地） | 不用服务端 VAD |
| 语音推理运行时 | **onnxruntime-android** | 仅承载 Silero VAD，版本在引入时确定（v1.4 补登记） |
| 异步 | Coroutine + Flow | 禁止 RxJava |
| UI | **Jetpack Compose** | 本版全面用 Compose，不再写 XML；MVI 状态驱动重组 |
| PDF 生成 | **原生 android.graphics.pdf.PdfDocument** | 不引入 iText/PDFBox 等第三方 PDF 库 |
| Word 解析 | **poi-ooxml-lite**（Apache POI，仅读 docx） | 只用于转简易 HTML→WebView 截图，不引 PDFBox |
| 测试 | **JUnit4 + ktor-client-mock + koin-test + kotlinx-coroutines-test** | 仅 test 配置，不进入 release（v1.4 补登记） |

***

## 3. 模块结构

```
:app                             // 导航与页面组装（布局 + 依赖组合），不写业务逻辑与状态机
:base                            // MVI 基类、Koin、Ktor 封装、日志、BaseApplication
:common                          // 跨业务通用：导航封装、API 层、工具类
:features:image-compression      // 功能 1（图片压缩）
:features:duplicate-cleanup      // 功能 2（重复照片清理），UI 与 image-compression 同页（见 §3.2）
:features:file-conversion        // 功能 3+4（图片合并 PDF、文件转 PDF）
:features:voice-chat             // 功能 5（类豆包语音聊天）
```

### 模块依赖规则（单向，禁止反向）

```
app → base, common, features/*
feature → base, common
common → base
base → 无业务依赖
feature 之间  →  禁止互相依赖
```

### 模块职责

- **:base**

  - `BaseApplication`（Koin 启动入口）
  - `BaseViewModel<S, E>`：`StateFlow<S>` 持状态，`Channel<E>` 发副作用
  - `NetworkClient`（Ktor 封装）：对外只暴露 `get()` / `post()`，支持配置日志、请求头、全局错误处理
  - `LogUtil`（Koin 单例类）：仅 debug 打印，输出 `文件名:行号 级别 信息`，release 全砍

- **:common**

  - 导航组件封装：过滤重复跳转；返回首页时重置导航栈
  - `common/api/`：可选的 service 与 endpoint 常量定义位置（网络规范见 §5，位置不强制）
  - 通用工具：加密 SP 封装、权限请求基类、文件工具

- **:app**

  - 底部导航 3 tab：图片压缩 / 文件转换 / AI 语音聊天
  - tab 切换不重复入栈，返回首页时重置导航栈（实现见 `:common` 导航封装）
  - 重复照片清理与图片压缩**合并在同一个 tab 页面**内（见 §3.2）；该页面由 `:app` 组装，
    两个 feature 各自只提供 `XxxSection` composable 与 `XxxViewModel`，**互不依赖**

### 3.2 重复照片清理页面交互规范（与 image-compression 同页）

UI 容器：`LazyVerticalGrid`，跨列头部分组：

```kotlin
items(groupItems) { group ->
    item(span = { GridItemSpan(maxLineSpan) }) {
        GroupHeader(group.title)   // 横跨整行的标题
    }
    items(group.photos) { photo ->
        PhotoGridItem(photo, onSelected = { ... })
    }
}
```

交互状态机（按钮文案驱动）：

1. 初始：主按钮显示 **【查询相同图片】**
2. 点击 → 按钮变为 **【重新查询】**，后台扫描（dHash/pHash + 汉明距离 ≤5 分组）
3. 扫描完成 → 按钮下方展示结果 Grid
4. 每张图片：

  - 点击图片本体 → 全屏放大预览
  - 右下角选中框 → 点选后右上角/右侧出现 **【删除】** 按钮

5. 点击 **【删除】** → 二次确认后删除选中图片

约束：

- 扫描全程本地、不联网
- **绝对不能自动删除**，必须用户手动勾选确认
- 删除前调用 MediaStore 回收站（Android 11+），可恢复，不直接 unlink
- 大图分批加载（Coil + 分页），防止 OOM
- **页面归属**：整页由 `:app` 组装，`image-compression` 与 `duplicate-cleanup` 各自只暴露
  `Section` composable 与 `ViewModel`，两者**互不依赖**（v1.4 定案）

### 3.3 文件转换能力分级（file-conversion）

| 输入格式 | 方案 | 状态 |
| --- | --- | --- |
| 图片（JPG/PNG/WebP/HEIC）→ PDF | 原生 `PdfDocument`，逐页 draw Bitmap | ✅ v1 实现 |
| 文本（TXT/MD）→ PDF | 原生 `PdfDocument`，Canvas 画文字 | ✅ v1 实现 |
| Word（DOCX）→ PDF | **截长图曲线方案**（见 §3.4） | ✅ v1 实现（.docx 优先，.doc 后续） |
| Excel（XLS/XLSX）→ PDF | 同上思路，表格转 HTML | ⏳ v1.1 再做 |
| PPT（PPT/PPTX）→ PDF | 自由排版，截长图方案不适用 | ❌ v1 不做 |

### 3.4 Word 转 PDF：截长图曲线方案

思路：不追求矢量 PDF，**把 Word 渲染成长图，再塞进 PdfDocument**。绕开完整 Office 排版引擎。

链路：

```
.docx 文件
  → Apache POI (poi-ooxml-lite) 读取段落/标题/表格/内嵌图片
  → 拼成简易 HTML（支持 h1/h2/p/strong/table/img base64）
  → WebView.loadDataWithBaseURL(html)
  → 测量 WebView 内容高度，动态设高
  → PixelCopy / drawToBitmap 截长图（API 26+ 用 PixelCopy）
  → 按 A4 比例切分成多页 Bitmap
  → PdfDocument 逐页 draw
```

依赖与约束：

- 引入 `poi-ooxml-lite`（约 5~10MB，剔除 examples/字体）；不引 PDFBox、不引完整 POI scratchpad
- 只支持 `.docx`；老 `.doc`（OLE2）v1 不做
- WebView 长截图统一用 `PixelCopy`（API 26+），低版本降级 `drawToBitmap`
- 复杂排版还原不了：浮动文本框、公式、艺术字、页眉页脚、复杂分栏——v1 接受
- 内嵌图片：从 docx `word/media/` 读出来转 base64 嵌进 `<img>`
- 输出 PDF 是**位图 PDF**，文字不可选中、不可复制——UI 文案必须提前告知用户

**为什么这个方案可行**：你不需要"完美还原 Word"，只需要"用户能看到内容、能存档"。截长图路线用 5~10MB 依赖换了 Word 支持，比 LibreOfficeKit 的 300MB 代价小两个数量级。

***

## 4. MVI 架构规范（base 模块强制）

每个 ViewModel **必须继承 `BaseViewModel<S, E>`**，并且：

1. 定义 `State`（data class，不可变）和 `Effect`（sealed interface，一次性事件）
2. 状态容器：`private val _uiState = MutableStateFlow(State())`，对外暴露 `uiState: StateFlow<...>`
3. 副作用：`private val _effect = Channel<Effect>(Channel.BUFFERED)`，对外 `effect: Flow<Effect>`
4. 禁止在 ViewModel 里持有 View / Context（用 `@ApplicationContext` 或参数传入）
5. 所有业务调用走 `viewModelScope.launch`，异常统一走 `CoroutineExceptionHandler` 或封装的 `runCatching`
6. Intent 由 UI 层单向发送：`viewModel.dispatch(Action.Xxx)`

***

## 5. 网络层规范

- HTTP 请求统一使用 `:base` 提供的 `NetworkClient`（只暴露 `get` / `post`）；service interface 允许定义在 `common/api/` 或 feature 模块内部，**位置不强制**
  <!-- v1.3 移除「所有 HTTP 请求只能在 common/api/ 下定义 service interface」的强制约束 -->
- feature 模块**禁止**直接 `HttpClient.get/post`、禁止直接 import Ktor 的 request builder，也禁止自行构造客户端
- 统一错误模型：sealed class `ApiResult<T>` / `NetworkError`，业务层不直接捕 `IOException`
- 请求头、超时、日志开关在 base 的 Ktor 配置里统一管理
- endpoint 常量集中在 `common/api/OpenAiEndpoints.kt`（或同名常量类），禁止业务模块写死 URL

***

## 6. 代码复用规则（补充原规范）

1. 同一 UI 模式 / 同一逻辑**出现第 2 次**就必须抽公共（比原规范的"第 3 次"更严，因为本项目模块多、重复风险高）
2. API 调用统一走 `NetworkClient`（或基于它封装的 service 接口），组件里不准直接 fetch
3. 工具函数放 `common/util/`，业务层禁止复制粘贴
4. 跨 feature 需要复用的业务逻辑，下沉到 common 或抽 UseCase，**feature 之间不互引**
5. 新增公共组件前，先在 common 里搜；找不到再写，写了要登记到第 11 节清单

***

## 7. 语音聊天模块（voice-chat）硬约束

链路固定：

```
AudioRecord(PCM 16k/24k)
  → Silero VAD 判断说完
  → Whisper STT
  → GPT-4o-mini（带上下文）
  → TTS 接口拿音频
  → AudioTrack 播放
```

- API Key：**EncryptedSharedPreferences**，禁止 DataStore / MMKV / 明文
- 会话历史：**Room**，库建在 `:features:voice-chat` 内部，不暴露给其他模块
- 必须有"测试连通性"按钮（调 `/v1/models`）
- 所有日志打印 API Key 时必须脱敏（`sk-***xxxx`）
- 必须在 UI 层提示："本工具调用您自己的 OpenAI 账号，费用由您承担"

***

## 8. 权限与隐私合规（Google Play 上架红线）

权限最小化：

- `RECORD_AUDIO`（仅 voice-chat 运行时申请）
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED`（Android 13+）
- `READ_EXTERNAL_STORAGE`（Android 12 及以下，maxSdk=32）
- 不要申请 `WRITE_EXTERNAL_STORAGE`（Scoped Storage）
- 不要申请无关权限（通讯录、定位、手机状态）

合规要求：

- 隐私政策明确说明：麦克风音频直传 OpenAI，不经过我方服务器
- 首次进入语音聊天前弹同意框
- 图片压缩、重复照片清理全程本地处理，不联网

***

## 9. 禁止事项（Code Review 一票否决）

- ❌ feature 模块直接 new Ktor client / 直接写 URL
- ❌ API Key 明文存储或打日志
- ❌ feature 之间互相 import
- ❌ 在 ViewModel / 工具类里 `startActivity` / `Toast`
- ❌ 复制粘贴超过 2 次的相同逻辑
- ❌ 引入文档第 2 节以外的框架（如要加，先改本文档）
- ❌ 在 release 包留 `Log.d` / `printStackTrace`
- ❌ 用 Java 写新业务
- ❌ 为了"复用"在公共组件里加 `if (feature == X)` 分支
- ❌ 在 `:app` 里写业务状态机或业务规则（只允许布局与依赖组合）

***

## 10. 待拍板事项

### 已决（累积，截至 v1.4）

- ✅ 重复照片清理：新建 `:features:duplicate-cleanup`，UI 与 image-compression 同页；
  **页面由 `:app` 组装，两个 feature 互不依赖**（v1.4 定案，见 §3 模块职责）
- ✅ PDF 生成：原生 `PdfDocument`
- ✅ UI 技术栈：Jetpack Compose（替换原 ViewBinding+XML）
- ✅ 文件转 PDF 输入范围：图片 / 文本 / Word（v1）；Excel 延后 v1.1；**PPT v1 不做**（见 §3.3）
- ✅ Git 提交规范：启用 Conventional Commits（feat/fix/docs/refactor/test/chore）

### 待你拍板

1. ~~Office 转 PDF 方案~~ → 已定：A + Word 截长图曲线方案（§3.4）；Excel v1.1，PPT v1 不做
2. ✅ **包名**：定为 `com.foolproof`（v1.3 已决，不再变更；模块包名 `com.foolproof.base` 等）
3. **minSdk / targetSdk / compileSdk**：目前工程为 `minSdk=24 / compileSdk=37 / targetSdk=37`；
   原建议 targetSdk=34 与现状不符，需拍板是否回到 34
4. **Room 位置**：voice-chat 私有 OK；以后多 feature 共用时是否抽到 common？
5. **低版本能力边界**（v1.4 新列）：API 24–29 上「删除照片」（§3.2 依赖 API 30+ 的回收站）与
   「HEIC 解码」（需 API 28+）都不能按 v1 方案实现，需要决定：降级为不支持、或走 SAF
6. **ViewModel 异常策略**（v1.4 新列）：§4 要求统一异常处理，但 `BaseViewModel.launch` 目前未提供，
   需决定是在基类里统一捕获，还是改回各 VM 自行处理

***

## 11. 公共组件登记清单（持续维护）

| 组件 | 位置 | 用途 | 注册时间 |
| --- | --- | --- | --- |
| LogUtil | base/log | 统一日志（Koin 单例，class 非 object） | 初始 |
| HttpClient | base/network | Ktor 封装（工厂：`createHttpClient`） | 初始 |
| NavHost 封装 | common/navigation | 导航 | 初始 |
| EncryptedSP 封装 | common/util | API Key 存储 | 初始 |
| BaseViewModel\<S, E\> | base/mvi | MVI 基类（StateFlow + Channel） | 2026-10-20（001-base-module） |
| MviContract（UiState / UiEffect / UiAction） | base/mvi | MVI 标记接口 | 2026-10-20（001-base-module） |
| NetworkClient | base/network | 唯一网络出口，只暴露 get/post | 2026-10-20（001-base-module） |
| ApiResult / NetworkError | base/network | 统一结果与错误模型 | 2026-10-20（001-base-module） |
| BaseApplication | base | Koin 启动（幂等） | 2026-10-20（001-base-module） |
| （后续新增在此登记） |  |  |  |

***

## 12. 版本

- v1.0（2026-10-20）：初始版本，锁定技术栈与模块结构
- v1.1（2026-10-20）：

  - 确认新建 `:features:duplicate-cleanup` 模块
  - UI 从 ViewBinding+XML 改为 Jetpack Compose
  - PDF 生成锁定原生 `PdfDocument`
  - 新增 §3.2 重复照片清理页面交互规范（LazyVerticalGrid + 状态机）
  - 新增 §3.3 文件转换能力分级，Office 转 PDF 调研结论（A/B/C 三选一待拍板）

- v1.2（2026-10-20）：

  - 拍板 Office 方案：v1 砍掉 LibreOfficeKit/Spire/完整 Office 引擎
  - Word 改用**截长图曲线方案**（§3.4）：poi-ooxml-lite 读 docx → 简易 HTML → WebView → PixelCopy 长截图 → PdfDocument
  - 新增依赖：`poi-ooxml-lite`（约 5~10MB），不引 PDFBox
  - 能力范围：v1 支持图片/文本/docx 转 PDF；Excel 延后 v1.1；PPT v1 不做
  - 明确输出为位图 PDF（文字不可选中），UI 需告知用户

- v1.3（2026-10-20，随 `001-base-module` 落地修订）：

  - **§3/§11**：`LogUtil` 由 `object` 改为 **Koin 提供的单例类**，业务侧注入使用。
    动机：原写法与 §5「依赖经 Koin 提供」自相矛盾，统一为注入后两处一致，且 sink/开关可被测试替换。
  - **§5**：移除「所有 HTTP 请求只能在 `common/api/` 下定义 service interface」的强制约束；
    service 可定义在 `common/api/` 或 feature 内部，但**仍禁止**直接操作 `HttpClient` 或自建客户端。
    动机：base 已提供唯一出口 `NetworkClient`，强制目录位置没有带来额外收益，反而增加跳转成本。
  - **§6**：代码复用规则第 2 条同步改为「API 调用统一走 `NetworkClient`」。
  - **§10-2**：包名定为 `com.foolproof`（不再变更）。

- v1.4（2026-10-20，跨章节一致性校验后修订）：

  - **§3 / §9（C1 定案）**：解决「同页」与「feature 互不依赖」的矛盾——整页由 **`:app` 组装**，
    feature 各自只提供 `Section` + `ViewModel`。`:app` 的角色由「只做壳，不写业务」
    收紧为「只做导航与页面组装，不写业务逻辑与状态机」，并在 §9 增加对应一票否决项。
    动机：方案对比过「允许 feature 单向依赖」「合并模块」「改二级页」，前者松动依赖底座且不可逆，
    后两者分别牺牲模块粒度与产品交互。
  - **§3**：修正 `common/api/` 的过期描述（v1.3 已取消「唯一出口」，§3 仍写着，且章节引用错标 §6）。
  - **§3**：`NetworkClient` 术语统一（原只写「Ktor HttpClient 封装」）、日志格式补「级别」、
    删除与 §3.2 重复的入栈表述。
  - **§4**：明确「必须继承 `BaseViewModel<S, E>`」；`Effect` 由 `sealed class` 更正为 `sealed interface`（与实现一致）。
  - **§2**：补登记 `onnxruntime-android`（§7 已锁定 Silero VAD 用 ONNX，缺登记会与 §9「禁止第 2 节外框架」冲突）
    与测试框架（JUnit4 / ktor-client-mock / koin-test / coroutines-test，仅 test 配置）。
  - **§10**：标题改为「已决（累积，截至 v1.4）」；修正「输入范围含 PPT」与 §3.3「PPT v1 不做」的矛盾；
    Conventional Commits 标记为已启用；新增两项待拍板（低版本能力边界、ViewModel 异常策略）。
