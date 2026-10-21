# 项目宪法（Project Constitution）

> 本文档是本项目的最高约束。任何代码、评审、重构都不得违反；如需修改，必须显式更新本文档并说明理由。
技术栈一旦锁定，禁止替换；新增依赖必须在第 2 节登记。

***

## 1. 项目定位：做什么 / 不做什么

### 1.1 做什么

纯本地工具 App，6 个功能：

1. 图片压缩（自研：平台解码 + 重编码，输出 WebP/JPEG）
2. 重复照片清理（dHash/pHash + 汉明距离分组）
3. 多张图片合并生成 PDF
4. 本地文件转 PDF
5. 类豆包语音聊天（用户自填 OpenAI API Key，回合制：Whisper → GPT-4o-mini → TTS）
6. 视频压缩（Media3 Transformer：预设分辨率/码率档位 + 裁剪时长，v1.13 新增）

### 1.2 不做什么（红线，本版不实现）

- ❌ 不写后端服务、不买服务器、不做用户账号体系
- ❌ 不做云同步、不做相册备份、不做社交分享
- ❌ 不内置 OpenAI Key，不代理、不转卖 API 调用
- ❌ 不做通用 AI 聊天机器人（仅限语音问答形态）
- ❌ 不做视频剪辑 / 多轨编辑 / 变速 / 视频转 GIF 等**压缩与裁剪时长以外**的视频处理
- ❌ 不做云盘、不做笔记类功能

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
| 图片压缩 | **自研**（`BitmapFactory` + `Bitmap.compress`） | 不引第三方压缩库；`androidx.exifinterface` 仅用于纠正方向 |
| 视频压缩 | **Media3 Transformer** 1.11.1（+ `media3-effect`） | 官方 AndroidX，Apache-2.0；只做转码/缩放/裁剪，不引 FFmpeg |
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
:features:video-compression      // 功能 6（视频压缩：转码 + 缩放 + 裁剪时长，v1.13）
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

  > **尚未创建（v1.15 明确）**。目前它的三项职责都还没有消费者：导航只是 tab 状态切换，
  > 没有 NavHost/返回栈可封装；`common/api` 只有语音聊天需要；权限基类只有重复清理一个调用方；
  > 加密 SP 只在保存 API Key 时需要。**建立触发条件**（满足任一即建，并在该 feature 的 tasks 里
  > 排成前置任务）：
  >
  > 1. 增加第 3 个 tab（文件转换）——把 `:app` 的 tab host 迁入，兑现「禁止业务模块自己写 NavHost」；
  > 2. 开始语音聊天——`common/api` + 加密 SP；
  > 3. 第二个 feature 需要运行时权限——权限基类。

- **:app**

  - 底部导航 **4 个 tab**：图片压缩（含重复照片清理）/ 视频压缩 / 文件转换 / AI 语音聊天
    （v1.15 修订：原写「3 tab」漏了 v1.13 新增的视频压缩；当前已实现前两个，后两个未开始）
  - tab 切换不重复入栈，返回首页时重置导航栈（实现见 `:common` 导航封装；`:common` 建成前
    由 `:app` 的 `FoolproofScreen` 直接持有）
  - 重复照片清理与图片压缩**合并在同一个 tab 页面**内（见 §3.2）；该页面由 `:app` 组装，
    两个 feature 各自只提供 `XxxSection` composable 与 `XxxViewModel`，**互不依赖**

### 3.2 重复照片清理页面交互规范（与 image-compression 同页）

UI 容器：三列网格 + 跨列头部分组（**纵向顺序：图片压缩区在上，重复照片清理区在下**，v1.8 定案）：

> **整页滚动归 `:app`（v1.11 / v1.12）**：整页只有一个滚动容器，由 `:app` 持有
> （当前是 `CompressionTabScreen` 里的 `LazyVerticalGrid`；v1.15 复核确认与实现一致）。
>
> - feature 的 Section **不得自带可滚动容器**（`LazyColumn` / `LazyVerticalGrid` / `LazyRow`），
>   否则会出现"两半各自滚动、整页不动"的体验问题；`verticalScroll` 内嵌懒加载容器还会因高度无界而崩溃。
> - 列表型内容由 feature 以 **`LazyGridScope` 扩展函数**贡献 item（如 `duplicatePhotoRows`），
>   由 `:app` 的网格统一懒加载；非列表部分可以作为整行 item 嵌入。
> - 网格缩略图 MUST 用系统缩略图（`loadThumbnail`）或采样解码，**禁止为格子解码原图**，
>   并应有内存缓存，避免滑出视口后重复解码。
> - 缩略图 MUST 完整显示整张图（按原图宽高比排版或 `ContentScale.Fit`），禁止 `Crop` 裁剪
>   ——用户要看清内容才敢删。

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
- **默认预选规则**（v1.10 定案）：每组预选「除最大一张以外的其余项」，用户可逐张改选。
  预选**不等于**自动删除——仍需点【删除】并二次确认，确认框必须写明张数与总大小。
- **删除按 API 版本分通道**（v1.6 定案，能力不同，不可统一实现）：
  - **API 30+**：**必须用 `MediaStore.createTrashRequest()`**（移入回收站、可恢复）；
    **禁止用 `createDeleteRequest()`**（永久删除）——两者 API 30+ 都存在，用错会让"可恢复"承诺失效（v1.14）
  - **API 24–29**：系统无回收站 API。改走 SAF（`ACTION_OPEN_DOCUMENT_TREE` + `DocumentsContract.deleteDocument`），
    用户需先授权目录；**删除不可恢复**，UI 必须在二次确认中明示「此操作不可撤销」
  - **API 24–29 且用户未授权 SAF 目录**：只展示重复分组，**删除按钮置灰**
- 任何版本都禁止用 `WRITE_EXTERNAL_STORAGE` 绕过（宪法 §8）
- 大图分批加载（Coil + 分页），防止 OOM
- **页面归属**：整页由 `:app` 组装，`image-compression` 与 `duplicate-cleanup` 各自只暴露
  `Section` composable 与 `ViewModel`，两者**互不依赖**（v1.4 定案）

### 3.3 文件转换能力分级（file-conversion）

| 输入格式 | 方案 | 状态 |
| --- | --- | --- |
| 图片（JPG/PNG/WebP/HEIC）→ PDF | 原生 `PdfDocument`，逐页 draw Bitmap | ✅ v1 实现（HEIC 限 API 28+，见下方门槛） |
| 文本（TXT/MD）→ PDF | 原生 `PdfDocument`，Canvas 画文字 | ✅ v1 实现 |
| Word（DOCX）→ PDF | **截长图曲线方案**（见 §3.4） | ⏳ 后续（2026-10-21 决定推迟，见 v1.16） |
| Excel（XLS/XLSX）→ PDF | 同上思路，表格转 HTML | ⏳ 后续（同样推迟） |
| PPT（PPT/PPTX）→ PDF | 自由排版，截长图方案不适用 | ❌ v1 不做 |

> **v1.16 修订**：Word / Excel 原定 v1 实现（DOCX 标为 ✅），负责人于 2026-10-21 决定
> **先只做图片与文本/Markdown，任意文档（docx/xlsx）留到后续**。理由：先交付零依赖、
> 覆盖最高频场景的部分；docx 需要 POI（+5–10MB）与 WebView 长截图链路，属于独立工作量，
> 混在同一轮会拖慢验证。§3.4 的设计保留，等后续做时直接采用，不必重新论证。


**HEIC 版本门槛（v1.7 定案）**：HEIC 解码依赖平台能力（`ImageDecoder` 为 API 28+，且需设备带 HEIF 编解码器），
因此**所有涉及 HEIC 输入的能力**——图片压缩的源图、重复照片扫描、转 PDF——在 **API 24–27** 上
必须明确提示「当前系统不支持 HEIC 格式」并跳过该文件，**不得静默失败、不得崩溃**。
不引入 `libheif` 等 native 解码库（无官方 Android 制品，且涉及 LGPL-3.0 与 HEVC 专利合规成本）。

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
5. 所有业务调用走基类的 `launch {}`（内部已捕获非取消异常并交给 `onError`），异常 **MUST NOT** 裸抛到
   `viewModelScope`；子类可覆写 `onError`，把错误映射为状态或一次性副作用
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

### 待你拍板（截至 v1.7：已全部拍板，无遗留项）

1. ~~Office 转 PDF 方案~~ → 已定：A + Word 截长图曲线方案（§3.4）；Excel v1.1，PPT v1 不做
2. ✅ **包名**：定为 `com.foolproof`（v1.3 已决，不再变更；模块包名 `com.foolproof.base` 等）
3. ✅ **SDK 口径**（v1.6 已决）：保持工程现状 `minSdk=24 / compileSdk=37 / targetSdk=37`；
   原「建议 targetSdk=34」作废。minSdk 是否需要上调，见第 5 条
4. ✅ **Room 位置**（v1.6 已决）：先保持在 `:features:voice-chat` 私有，不抽到 common；
   等出现第二个真正需要 Room 的 feature 时再评估（宪法 §7 YAGNI）
5. **低版本能力边界**（拆分处理）：
   - ✅ **删除照片**（v1.6 已定）：API 30+ 走 MediaStore 回收站；API 24–29 走 SAF 且明确不可恢复，见 §3.2
   - ✅ **HEIC 解码**（v1.7 已定）：采用降级方案——API 28+ 用平台解码，API 24–27 明确提示不支持
     （见 §3.3 门槛说明）；不引入 native 解码库
6. ~~**ViewModel 异常策略**~~ → **已定（v1.5）**：由 `BaseViewModel.launch {}` 统一捕获非取消异常，
   默认写日志、子类可覆写 `onError` 做状态/副作用映射；业务代码不再直接调用 `viewModelScope.launch`。

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
| AndroidImageCompressor | features/image-compression/data | 自研压缩实现（采样 + 重编码 + EXIF 方向） | 2026-10-21（002-image-compression） |
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

- v1.5（2026-10-20）：

  - **§4-5**：协程异常策略定案并落地——`BaseViewModel.launch {}` 统一捕获非取消异常交给 `onError`，
    默认写 `LogUtil`，子类可覆写映射为状态/副作用；取消异常照常向上传播。
    动机：原条文只写「统一走 CoroutineExceptionHandler 或 runCatching」，但基类并未提供，
    业务侧直接 `viewModelScope.launch` 会把异常裸抛成崩溃。
  - **§10-6**：标记为已定；§10 尚未拍板的只剩「targetSdk 口径」「Room 位置」「低版本能力边界」三项。

- v1.6（2026-10-20）：

  - **§10-3**：SDK 口径定为工程现状 `minSdk=24 / compileSdk=37 / targetSdk=37`，撤回「targetSdk=34」的建议。
  - **§10-4**：Room 先保留在 `:features:voice-chat` 私有，不抽 common；等第二个使用者出现再评估。
  - **§3.2 / §10-5（删除部分）**：删除能力按 API 分通道——API 30+ 用 MediaStore 回收站（可恢复），
    API 24–29 用 SAF（不可恢复，UI 必须明示），未授权目录时按钮置灰；任何版本都不申请写权限。
    动机：回收站 API 是 API 30 才有的系统能力，低版本无法伪装；申请写权限又与 §8 冲突。
  - **§10-5（HEIC 部分）**：查证后 `libheif` 无官方 Android 制品，原「补依赖解 HEIC」方案不成立，
    留待单独决定。

- v1.7（2026-10-20）：

  - **§3.3 / §10-5（HEIC）**：定案为降级方案——API 28+ 依赖平台解码，API 24–27 明确提示「系统不支持」，
    并跳过该文件；不引入 `libheif` 等 native 库。
    动机：libheif 无官方 Android 制品（Maven Central 无、JitPack 返回空、`androidx.heifwriter` 只能编码），
    自建 native 需承担 LGPL-3.0 合规与 HEVC 专利评估，为极少数老设备上的 HEIC 场景不划算。
  - 该门槛同时适用于图片压缩源图、重复照片扫描与转 PDF 三处 HEIC 输入，不只是 §3.3。

- v1.8（2026-10-20）：

  - **§3.2**：明确同页的纵向顺序——图片压缩区在上、重复照片清理区在下（`002-image-compression` 规格拍板）。
    动机：页面结构归 §3.2 管，顺序若只写在 feature 规格里，将来两个模块各写一遍就会漂移。

- v1.9（2026-10-21）：

  - **§2 / §1.1**：图片压缩由「Compressor (zetbaitsu)」改为**自研**（`BitmapFactory` 采样解码 +
    `Bitmap.compress` 编码，`androidx.exifinterface` 仅用于纠正拍照方向）。
    动机：① Compressor 自 2021 年停更，且实测存在两个隐藏行为（默认把图缩到 612×816、
    输入无扩展名且输出扩展名变化时会写到不可写路径），需要额外规避；
    ② 社区替代 Luban 2（`top.zibin:luban:2.0.3`，维护中）的 API **不接受质量/格式/尺寸参数**，
    其策略是自动压到「微信朋友圈画质」，与 §1.1 要求的「用户可选 WebP/JPEG」不符，不能平替；
    ③ 自研仅依赖平台 API，无 native 库、无停更风险，且既有仪器化测试可原样复用。

- v1.10（2026-10-21）：

  - **§3.2**：明确重复照片清理的**默认预选规则**——每组预选「除最大一张以外的其余项」，用户可改选；
    预选不等于自动删除，删除仍需二次确认。动机：`003-duplicate-cleanup` 规格里这一条与
    「必须用户手动勾选确认」的字面表述存在张力，若只写在 spec 里，实现者可能按旧条理解成「默认全不选」。

- v1.11（2026-10-21）：

  - **§3.2**：新增「整页滚动归 `:app`」规则——整页只允许一个滚动容器，feature 的 Section
    禁止自带 `LazyColumn` / `LazyVerticalGrid`；并把网格容器的表述从「`LazyVerticalGrid`」改为
    「三列网格 + 跨列组头」。同时规定缩略图必须 `ContentScale.Fit` 完整显示，禁止 `Crop` 裁剪。
    动机：初版把整页拆成两个 `weight(1f)` 的半屏区块、各自内部滚动，用户反馈「无法整体滑动」；
    而 `verticalScroll` 内嵌懒加载容器会因高度无界崩溃，所以规则必须落在「谁是唯一滚动者」上。
    缩略图裁剪则会让用户看不清内容却要决定删除，风险不对等。

- v1.16（2026-10-21）：

  - **§3.3**：Word（DOCX）/ Excel（XLSX）→ PDF 的状态由「✅ v1 实现」改为「⏳ 后续」。
    动机：负责人明确「先做图片、文本/Markdown → PDF，任意文档（docx/xlsx）后续再做」。
    原表格让 DOCX 看起来是 v1 必做项，与新的排期直接冲突——宪法与需求冲突时必须显式修订，
    不能让规格悄悄违反宪法。§3.4 的截长图设计保留，后续实现时直接用。

- v1.15（2026-10-21，文档漂移校验后修订）：

  - **§3**：`:app` 的 tab 清单由「3 tab（图片压缩 / 文件转换 / AI 语音聊天）」更正为
    「4 个 tab（图片压缩含重复清理 / 视频压缩 / 文件转换 / AI 语音聊天）」，并注明已实现前两个。
    动机：v1.13 新增视频压缩时只改了 §1.1 / §2 / §3 的模块列表，漏改 tab 清单，
    导致宪法描述与实现不一致——宪法过期就不再是判据。
  - **§3**：为 `:common` 标注「尚未创建」及其三个**建立触发条件**（第 3 个 tab / 语音聊天 /
    第二个需要运行时权限的 feature）。动机：它的三项职责目前都没有消费者，提前建空模块
    只增加构建配置与依赖边，不产生价值；但也不能让它长期停留在"计划里"，因此把触发条件写死。
  - **§3.2**：复核 `CompressionTabScreen` 与实现一致，**未作修改**。
    （修订过程中一度误以为该名字过时、想改成另一个不存在的名字，核对代码后确认
    `app/src/main/java/com/foolproof/ui/CompressionTabScreen.kt` 真实存在且持有网格容器，
    已撤回该"更正"——改文档必须核对代码，不能凭印象。）

- v1.14（2026-10-21）：

  - **§3.2 删除通道**：明确 API ≥ 30 **必须用 `createTrashRequest`**，禁止 `createDeleteRequest`。
    动机：真机与 AOSP 双端验证时发现用后者是永久删除（系统对话框文案为「删除这张照片」），
    回收站里没有任何记录——"可恢复"承诺实际失效；改用前者后对话框变为「移入回收站」。
  - **§3.2 分组规则**：改为「**代表比较 + 精确哈希优先**」。
    动机：实测两份字节完全相同的图被分到不同组；而"与组内所有成员比较"又会把 26 张不同截图
    链式合并成一个 11 张大组（更危险）。折中：完全相同的哈希优先并入（仍受纯色保护约束），
    其余仍按代表比较。
  - **§3.2 哈希取图**：计算 dHash 的采样 MUST 用确定性解码（`BitmapFactory` + `inSampleSize`），
    **禁止用 `ContentResolver.loadThumbnail`**。动机：实测它对字节相同的两张图返回了不同像素，
    导致同一张图算出不同哈希。

- v1.13（2026-10-21）：

  - **§1.1 / §1.2**：新增功能 6「视频压缩」，并把红线从「不做视频处理」收窄为
    「不做视频剪辑 / 多轨 / 变速 / 转 GIF 等压缩与裁剪时长以外的处理」。
    动机：负责人要求增加视频压缩；原红线与需求冲突，必须显式修订而不是绕过。
  - **§2**：视频压缩锁定 **Media3 Transformer 1.11.1 + media3-effect**（Apache-2.0，官方维护，
    无 native 库）。评估过的替代方案与结论：
    - 纯自研 `MediaCodec + MediaMuxer`：零依赖，但要自行处理 PTS/EOS/封装/旋转/编码器回退，
      工期与真机兼容风险高，且 Transformer 已把这些做对；
    - FFmpeg（`dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.9`）：APK +8–12MB/ABI，软编在手机上不可用，
      x264 版本为 GPL，闭源分发有法务风险；
    - LightCompressor 1.3.3：仅 JitPack、版本旧，且 API < 29 要求 `WRITE_EXTERNAL_STORAGE`，
      与 §8「不申请写权限」直接冲突。
  - **§3**：新增 `:features:video-compression` 模块。

- v1.12（2026-10-21）：

  - **§3.2**：把「整页单一滚动」细化为——滚动/懒加载容器由 `:app` 持有（`LazyVerticalGrid`），
    feature 以 `LazyGridScope` 扩展贡献 item；缩略图必须用系统缩略图 + 内存缓存，禁止为格子解码原图。
    动机：v1.11 的"Section 用普通 Column 渲染"在数据量大时会一次性组合并解码全部缩略图，
    实测出现 200–250ms 的掉帧尖峰；改为逐行懒加载 + 系统缩略图后，90 分位从 26ms 降到 13ms，
    尖峰消失。同时保留"按原图比例完整显示"，不裁切。
