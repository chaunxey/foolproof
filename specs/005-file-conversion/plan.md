# Implementation Plan: 文件转 PDF（file-conversion）

**Feature**: `specs/005-file-conversion/spec.md`
**Created**: 2026-10-21
**Constitution**: v1.16（§2 技术栈、§3 模块边界、§8 权限红线、§4 MVI）

## 1. 技术上下文

| 项 | 值 |
|---|---|
| 语言 / 构建 | Kotlin 2.4.21、AGP 9.4.1、Gradle 9.8.2，compileSdk/targetSdk 37，minSdk 24，Java 11 |
| UI | Jetpack Compose（BOM 2026.09.00）+ Material3；MVI（`BaseViewModel`） |
| DI | Koin 4.2.2（`koinViewModel()`） |
| PDF 生成 | **框架自带 `android.graphics.pdf.PdfDocument`**，零新依赖 |
| 输入 | Photo Picker（`PickMultipleVisualMedia`）、SAF（`ACTION_OPEN_DOCUMENT`，`text/*`） |
| 输出 | 私有 `files/pdf/` + `FileProvider`；API ≥ 29 经 `MediaStore.Downloads` 存下载 |
| 新增依赖 | **无**（不引 PDFBox / iText / Markdown 解析库 / POI） |

## 2. 模块与依赖

```
:app
 ├── :common              （新建：导航封装；后续放 api/util）
 ├── :base
 └── :features
      ├── image-compression
      ├── duplicate-cleanup
      ├── video-compression
      └── file-conversion  （新建）
```

依赖方向（宪法 §3）：`feature → common, base`；`common → base`；`:app → 全部`。

### 2.1 `:common`（本次新建）

| 内容 | 说明 |
|---|---|
| `navigation/FoolproofDestination.kt` | tab 枚举：`Images` / `Videos` / `Converter`（语音后续补） |
| `navigation/FoolproofTabHost.kt` | 底部导航 + `NavigationBar`；**防重复入栈**、**回首页重置** |
| `di/CommonModule.kt` | Koin 模块（目前只注册导航相关，后续放 api/util） |
| `build.gradle.kts` | 与 `:base` 相同的 android library 配置 + compose |

### 2.2 `:features:file-conversion`（本次新建）

```
domain/
  PdfSource.kt          // ImageSource(uri,width,height) | TextSource(uri,name,bytes)
  PdfPageSpec.kt        // A4 尺寸、边距、页面类型
  PdfResult.kt          // path / bytes / pageCount
  PdfConverter.kt       // interface：convert(sources, mode): Flow<PdfProgress>
  ImageLoader.kt        // interface：加载并采样图片（便于单测替换）
  TextSourceLoader.kt   // interface：读取文本 + 编码判定
data/
  NativePdfGenerator.kt     // PdfDocument 绘制（图片页 / 文本页）
  BitmapImageLoader.kt      // BitmapFactory + inSampleSize + EXIF 方向修正
  ContentUriTextLoader.kt   // SAF 读取 + UTF-8/GBK 回退
  MarkdownParser.kt         // 极简 Markdown → 排版元素（纯逻辑，可单测）
  TextLayout.kt             // 中英文混排换行 + 分页（纯逻辑，可单测）
  MediaStorePdfExporter.kt  // 存下载（API ≥ 29）
  PdfOutputCleaner.kt       // 启动清理残留
ui/
  FileConversionSection.kt  // 对外唯一入口
  ModeSelector.kt           // 图片模式 / 文本模式
  MarkdownPreview.kt        // 文本预览（可选，先只显示文件信息）
ViewModel + Contract + di/FileConversionModule.kt
```

## 3. 设计决策

### D1. 新建 `:common` 并把 tab host 迁入

宪法 v1.15 的触发条件「增加第 3 个 tab」已满足。本次：

1. 新建 `:common`（android library + compose + Koin 模块）；
2. 把 `:app/ui/FoolproofScreen.kt` 的底部导航抽成 `:common/navigation/FoolproofTabHost`；
3. `:app` 只负责把各 feature 的 `XxxSection` 传进去（`:app` 不写导航状态机）。

### D2. 导航实现：轻量 tab host（**待批准修订宪法 §2**）

宪法 §2 锁定「Jetpack Navigation 3」。但本应用是**3 个平级 tab、无嵌套、无深链**，
没有返回栈可言。Navigation 3 仍处 alpha，API 在多个版本间反复变动
（`NavDisplay` / `NavBackStack` 的构造与 key 类型都改过），为 3 个静态 tab 引入它是
**风险大于收益**。

方案：`:common` 内自研 `TabHost`（`NavigationBar` + 状态机），满足宪法 §3 的
「防重复入栈」「回首页重置」两条行为要求（用状态机天然满足：重复点同一 tab 不重建，
回首页即切回第一个 tab）。

> **需要负责人确认**：把 §2 的「导航」行从「Jetpack Navigation 3」改为
> 「轻量 tab host（`:common` 封装）；需要多页/深链导航时再引入 Navigation 3」，
> 并升 v1.17。若不同意，则退化为引入 Navigation 3 alpha，工期与风险都上浮。

### D3. PDF 生成：原生 `PdfDocument`

- A4 = 595×842 **pt**（`PdfDocument.PageInfo` 用 1/72 英寸单位）；
- 图片页：按原宽高比等比缩放居中，边距 24pt，保留 Letterbox；
- 文本页：边距 48pt，逐行 `drawText`，字体用 `Typeface.DEFAULT`（中文随系统字体回退）；
- 输出为 **图片页位图 + 文本页矢量文字**（文本可选中复制）。

### D4. Markdown 解析：自研极简版本

不引库（`commonmark` 会带 300KB+ 且 API 面大）。支持 spec FR-003 列举的子集：

```
# / ## / ###      标题（字号 22 / 18 / 15）
普通段落           14pt，行距 1.5
- / 1. 列表        前缀符号 + 缩进
**粗体** / *斜体*  仅在行内生效
`code`            等宽 + 灰底
``` 代码块 ```     等宽 12pt + 浅灰底 + 整块不跨页
---               分隔线
```

解析器输出 `List<Block>`（`Heading` / `Paragraph` / `ListBlock` / `CodeBlock` / `Divider`），
纯 Kotlin、无 Android 依赖 → 可直接 JVM 单测。

### D5. 文本排版与分页：自研

- 用 `Paint.breakText()` 逐行折行（比自研宽度估算可靠）；
- 超长无空格串：按字符强制断开（spec US2/AC3）；
- 分页：累计行高，超出内容高度前换页；**代码块整体移到下一页**而不是从中间切；
- 纯逻辑部分（分页决策）与绘制分离，便于单测。

### D6. 图片解码：采样 + EXIF

- `BitmapFactory.Options.inJustDecodeBounds` 读尺寸 → 计算 `inSampleSize`，使长边 ≤ 2048；
- `ExifInterface` 读 `TAG_ORIENTATION`，用 `Matrix` 旋转/镜像；
- 复用图片压缩模块已验证过的参数（长边 2048 对 A4 打印足够）。

### D7. 输出与清理

- 写 `files/pdf/foolproof_<时间戳>.pdf`；
- 分享：沿用现有唯一 FileProvider（authority `${applicationId}.files`），
  在 `:app` 的 `res/xml/file_paths.xml` 里增加 `<files-path name="pdf_output" path="pdf/" />`；
- 存下载（API ≥ 29）：`MediaStore.Downloads` + `IS_PENDING`，路径 `Downloads/Foolproof/`；
- 启动清理：沿视频模块已验证的模式——feature 暴露 `PdfOutputCleaner`（Koin 单例），
  由 `:app` 在 `onCreate` 的后台线程调用（与 `VideoOutputCleaner` 并列）。

### D8. 状态机与进度

`Idle → Ready(sources) → Converting(current,total) → Done(result) / Failed(reason)`；
取消时置 `Cancelled` 并删半成品；图片模式按页推进进度，文本模式按"解析/排版/绘制"三段推进。

## 4. 测试策略

| 层 | 内容 |
|---|---|
| JVM 单测 | Markdown 解析（各语法元素）、文本折行与分页、文件名生成、模式切换状态机、错误映射 |
| 仪器化 | 用 `PdfRenderer` 打开生成物：断言页数、页面尺寸（A4 点值）、文本页可渲染 |
| 真机/模拟器 | 5 张混合比例图片 → PDF 页序与不变形；中文 Markdown 无乱码；取消后无残留 |

## 5. 风险

| 风险 | 缓解 |
|---|---|
| 大图 OOM | 强制采样（长边 ≤ 2048），`inJustDecodeBounds` 先探测 |
| 中文断行错乱 | 用 `Paint.breakText` 而非字符宽度估算；单测覆盖中英混排 |
| Navigation 3 alpha 风险 | D2 建议不引入；若必须引入则锁死版本 |
| FileProvider 路径冲突 | 在 `:app` 统一维护 `file_paths.xml`，新增 `pdf/` 子目录 |
| 文本编码非 UTF-8 | 先按 UTF-8 严格解码，失败回退 GBK，再失败明确提示 |

## 6. 完成定义

- spec 中 19 条 FR、6 条 SC 全部有对应实现或测试；
- `:common` 建成且 `:app` 不再自己写导航状态机；
- 全仓单测绿；release 构建通过；
- 真机/模拟器各跑通一次「图片 → PDF」「Markdown → PDF」「存下载」。
