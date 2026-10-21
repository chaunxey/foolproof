# Foolproof

> **状态：规格阶段** — 宪法已定稿（v1.2），工程仍是 Android Studio 新建的 Compose 模板，模块骨架尚未搭建。

**简介**：纯本地的图片 / 文件处理工具 App。所有处理都在设备上完成，不依赖后端、不上传用户文件；
唯一需要联网的是「AI 语音聊天」，使用用户自己填写的 API Key，费用自负。

**目标功能**：

1. 图片压缩（自研编码，输出 WebP / JPEG）
2. 重复照片清理（dHash / pHash + 汉明距离分组，手动勾选删除，走 MediaStore 回收站）
3. 多张图片合并生成 PDF
4. 本地文件转 PDF（v1 支持图片 / 文本 / Word，Excel 延后，PPT 不做）
5. 类豆包语音聊天（录音 → Silero VAD → Whisper → GPT-4o-mini → TTS 播放）

> 完整范围、红线与方案取舍见 [宪法 `.specify/memory/constitution.md`](.specify/memory/constitution.md)。

---

## 技术栈

### 当前工程已引入

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | 使用 AGP 9 的新版 DSL（见下方「AGP 9 约定」） |
| Gradle | 9.8.2 | 已启用 Configuration Cache |
| Kotlin | 2.4.21 | 仅应用 Compose / Serialization 插件，Kotlin 编译由 AGP 内置支持 |
| Jetpack Compose | BOM 2026.09.00 | Material 3 + 动态取色（Android 12+） |
| Koin | 4.2.2 | `:base` 的依赖注入 |
| Ktor Client | 3.6.0 | `:base` 的统一网络出口（OkHttp 引擎） |
| kotlinx.serialization | 1.12.0 | JSON 序列化 |
| kotlinx.coroutines | 1.11.0 | 协程与测试构件 |
| compileSdk / targetSdk | 37 | 宪法 §10 建议 targetSdk=34，**待拍板** |
| minSdk | 24 | Android 7.0，与宪法一致 |
| JVM Target | Java 11 | 编译期 JDK 为 JetBrains Runtime 25 |

### 规划技术栈（宪法 §2 锁定，尚未引入）

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| 导航 | Jetpack Navigation 3 | 封装在 `:common`，业务模块不写 NavHost |
| 图片加载 | Coil 3 | 禁止在业务模块直接 new ImageLoader |
| 本地存储 | Room + EncryptedSharedPreferences | 会话历史 / API Key 存储 |
| 语音端点检测 | Silero VAD（ONNX，本地） | 不用服务端 VAD |
| PDF 生成 | 原生 `PdfDocument` | 不引 iText / PDFBox |
| Word 解析 | poi-ooxml-lite | 仅读 `.docx`，用于截长图转 PDF |
| 图片压缩 | Compressor（zetbaitsu） | 输出 WebP / JPEG |

技术栈由宪法锁定，**禁止替换，新增依赖须先改宪法第 2 节**。依赖版本统一在
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) 中以 Version Catalog 管理，**不要在 `build.gradle.kts` 里硬编码版本号**。

## 环境要求

- **JDK 17 或更高**（本机 IDE 使用 JetBrains Runtime 25，见 `.idea/misc.xml`）
- **Android SDK Platform 37** + Build-Tools（通过 Android Studio SDK Manager 安装）
- Android Studio（支持 AGP 9.4 的版本）

首次构建前需存在 `local.properties` 并指向本机 SDK：

```properties
sdk.dir=C\:\\path\\to\\your\\sdk
```

该文件由 Android Studio 自动生成，已被 `.gitignore` 排除，**不要提交**。

## 快速开始

```bash
# 克隆后直接用 wrapper 构建，无需预装 Gradle
git clone <repo-url> && cd Foolproof

# 构建 debug APK
./gradlew :app:assembleDebug

# 产物位置
# app/build/outputs/apk/debug/app-debug.apk
```

Windows 下用 `gradlew.bat`。安装到已连接设备/模拟器：

```bash
./gradlew :app:installDebug
```

日常开发建议直接用 Android Studio 打开项目根目录，它会自动识别为 Gradle 项目并生成 `app` 运行配置。

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew :app:assembleDebug` | 构建 debug APK |
| `./gradlew :app:installDebug` | 构建并安装到设备 |
| `./gradlew :app:testDebugUnitTest` | 运行本地单元测试（JVM） |
| `./gradlew :app:connectedDebugAndroidTest` | 运行设备端 UI 测试（需连接设备） |
| `./gradlew :app:lintDebug` | 静态检查 |
| `./gradlew :app:bundleRelease` | 构建 release AAB（发布用） |
| `./gradlew --stop` | 停止 Gradle 守护进程 |

## 项目结构

```
Foolproof/
├── app/                            # 应用壳
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/foolproof/
│       │   ├── FoolproofApp.kt          # 继承 BaseApplication，启动 Koin
│       │   ├── MainActivity.kt          # 入口 Activity（Compose）
│       │   └── ui/theme/                # Material 3 主题
│       ├── keepRules/rules.keep         # R8 混淆规则（AGP 9 新位置）
│       └── res/
├── base/                           # 架构基建库（详见下方「base 模块」）
│   ├── build.gradle.kts
│   ├── consumer-rules.pro          # 随 AAR 提供给消费方的 R8 规则
│   └── src/
│       ├── main/java/com/foolproof/base/
│       │   ├── BaseApplication.kt        # Koin 启动（幂等）
│       │   ├── di/BaseModule.kt          # 容器装配
│       │   ├── log/LogUtil.kt            # debug-only 日志
│       │   ├── mvi/                      # MviContract + BaseViewModel
│       │   └── network/                  # Ktor 封装 + ApiResult + NetworkError
│       └── test/java/com/foolproof/base/ # 33 个单元测试
├── features/
│   ├── image-compression/          # 功能 1：图片压缩
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/java/com/foolproof/features/compression/
│   │       │   ├── CompressionSection.kt     # 对外唯一 Composable
│   │       │   ├── CompressionViewModel.kt   # MVI + 批量串行 + 防抖预估
│   │       │   ├── domain/                   # 模型与三个接口（测试接缝）
│   │       │   └── data/                     # 自研压缩 / MediaStore / 元数据读取
│   │       └── test/java/...                 # 30 个单元测试 + 4 条仪器化测试
│   └── duplicate-cleanup/          # 功能 2：重复照片清理
│       ├── build.gradle.kts
│       └── src/
│           ├── main/java/com/foolproof/features/duplicates/
│           │   ├── DuplicateSection.kt       # 对外唯一 Composable
│           │   ├── DuplicateViewModel.kt     # MVI + 扫描状态机 + 删除流程
│           │   ├── domain/                   # dHash / 分组 / 预选（纯函数）+ 两个接口
│           │   ├── data/                     # MediaStore 扫描与删除、缩略图采样
│           │   └── ui/                       # 分组网格 + 全屏预览
│           └── test/java/...                 # 31 个单元测试
├── docs/requirements.md            # 原始需求记录（已由宪法取代）
├── specs/
│   ├── 001-base-module/            # base 模块的 spec / plan / tasks
│   ├── 002-image-compression/      # 图片压缩的 spec / plan / tasks
│   └── 003-duplicate-cleanup/      # 重复照片清理的 spec / plan / tasks
├── gradle/
│   ├── libs.versions.toml          # 依赖版本目录
│   └── wrapper/                    # Gradle Wrapper 9.8.2
├── .specify/                       # spec-kit：模板、脚本、工作流、宪法
├── .dsh/skills/                    # 本项目的 speckit 命令技能
├── .gitattributes                  # 换行符规范（gradlew 保持 LF）
└── build.gradle.kts                # 根构建脚本
```

## 模块规划（宪法 §3）

```
:app                             # 导航与页面组装（不写业务逻辑）      ✅ 已建
:base                            # MVI 基类、Koin、Ktor 封装、日志  ✅ 已建
:features:image-compression      # 功能 1：图片压缩                  ✅ 已建
:common                          # 导航封装、common/api、通用工具    ⏳ 待建
:features:duplicate-cleanup      # 功能 2（与图片压缩同页，见 §3.2）  ✅ 已建
:features:file-conversion        # 功能 3 + 4
:features:voice-chat             # 功能 5
```

依赖方向单一，**禁止反向与横穿**：

```
app → base, common, features/*
feature → base, common        （feature 之间禁止互相依赖）
common → base
base → 无业务依赖
```

底部导航 3 个 tab：图片压缩 / 文件转换 / AI 语音聊天。

## 图片压缩（已实现）

`:features:image-compression`，只对外暴露一个 `CompressionSection`，整页由 `:app` 组装（宪法 §3.2）。

- **零权限**：选图走系统 Photo Picker，写图走应用私有目录 + `FileProvider`；不申请 `READ_MEDIA_*` / `WRITE_*`
- **压缩**：自研实现（`BitmapFactory` 采样 + `Bitmap.compress`），输出 JPEG / WebP，质量 0–100；
  拍照方向用 `androidx.exifinterface` 纠正，重编码后不保留 EXIF（含定位）
- **版本门槛**：HEIC 仅在 API ≥ 28 可解码，24–27 提示不支持；「保存到相册」仅 API ≥ 29 出现
- **行为**：批量串行压缩、单张失败隔离可重试、参数改动 300ms 防抖后出预估体积

> 为什么自研而不用现成库（宪法 §2 v1.9）：Compressor 已于 2021 年停更，且有两处隐藏行为需规避；
> Luban 2 虽在维护，但 API 不接受质量/格式/尺寸参数（自动压到「朋友圈画质」），与「可选参数」的规格不符。

## 重复照片清理（已实现）

与压缩同页，位于压缩区**下方**（宪法 §3.2 v1.8）。

- **判定**：8×8 dHash（64 bit）+ 汉明距离 **≤ 5** 分组；纯色/低方差图有额外保护
  （方差 < 16 视为低信息量，须宽高比接近且体积差 < 2% 才合并）
- **预选**：每组默认勾选「除体积最大一张以外的其余项」，用户可逐张改选（宪法 v1.10）；
  **绝不自动删除**，删除前必弹二次确认并显示张数与总大小
- **删除**：API ≥ 30 走 `MediaStore.createDeleteRequest`（回收站、可恢复）；
  API ≤ 29 走 SAF 且**不可恢复**，未授权目录时删除入口置灰
- **结果以查询为准**：系统回调码不保证每张都删成功，删除后重新查询这些 Uri 是否存在
- **权限**：仅 `READ_MEDIA_IMAGES`（33+）/ `READ_EXTERNAL_STORAGE`（≤32），**不申请任何写权限**
- **性能**：API ≥ 29 用 `loadThumbnail` 走系统缩略图缓存；扫描分批、显示进度、可取消
- **滚动与排版**（宪法 §3.2 v1.11/v1.12）：整页只有 `:app` 的一个 `LazyVerticalGrid` 负责滚动，
  清理区逐行懒加载；缩略图用系统缩略图 + 内存缓存（不为格子解码原图），并按原图宽高比铺满整行
  ——既不裁剪，也不会因一次解码上百张图而掉帧

## 视频压缩（已实现）

独立 tab（宪法 §3；不与图片压缩同页）。技术栈：**Media3 Transformer 1.11.1 + media3-effect**（宪法 §2 v1.13）。

- **档位**：480p / 720p / 1080p × 高 / 中 / 低码率（H.264 + AAC 128k）；
  分辨率高于源时自动降档（不放大），编码器能力不足时也会降档并在界面提示
- **裁剪**：双滑块选起止（最小 1 秒），`ClippingConfiguration` 与 `Presentation` 在同一次转码中完成
- **预览播放**：选中后不自动播放，点画面播放/暂停；播放中隐藏按钮，底部进度条按裁剪区间走，
  播到终点自动停并回起点（`ui.VideoPreview`，与结果预览共用）
- **结果对比**：压缩完成后可在弹窗里预览压缩结果（「原 → 新 · 时长」），主页面状态不变；
  结果不小于源文件时会明确提示「比原文件大」并给出差额
- **预估**：`(视频码率 + 音频码率) × 裁剪后时长 ÷ 8`，压缩前即可看到大概体积
- **进度与取消**：Media3 无进度回调，每 400ms 轮询 `getProgress`；取消是异步的，
  实现里会重试删除半成品（见 `Media3VideoCompressor.cleanupPartialFile`）
- **必须硬编**：只有软件编码器时**如实提示**"压缩时长会比较长"，不静默降级；
  真机实测 `c2.mtk.avc.encoder`（硬编，最大 7680×7680），模拟器只有软编
- **输出**：应用私有 `files/videos/` → 分享走 FileProvider（authority `<包名>.files`，
  由 `:app` 统一声明）；存相册仅 API ≥ 29，经 MediaStore（`IS_PENDING`），**不申请写权限**
- **权限面**：Media3 会带入 `ACCESS_NETWORK_STATE` 与 `WAKE_LOCK`，本项目用不到，
  已在 `:app` 的 manifest 里用 `tools:node="remove"` 显式移除（宪法 §8 权限最小化）
- **前台限制**：离开页面即取消，不引 WorkManager / 前台服务

**已知偏差**：极短视频（几秒）的实际体积可能比预估高 20% 以上——容器开销与 I 帧占比在短片段里被放大。
预估公式更适合分钟级视频。

**已知局限（v1 接受）**：dHash 比较水平相邻像素，水平镜像会使哈希近似按位取反（距离接近 64），
因此镜像副本**不会**被识别为重复——这是漏判，不是误判，不会误删用户的图。

## base 模块

`base` 提供四件地基，业务模块只依赖它、不重复造轮子。

**MVI**：继承 `BaseViewModel<S, E>`，状态用 `updateState`，副作用用 `sendEffect`；
协程用基类的 `launch {}`，异常会被统一捕获并交给 `onError`（默写日志，可覆写）。

```kotlin
data class PhotoState(val count: Int = 0) : UiState
sealed interface PhotoEffect : UiEffect { data object Saved : PhotoEffect }

class PhotoViewModel(logger: LogUtil) :
    BaseViewModel<PhotoState, PhotoEffect>(PhotoState(), logger) {

    override fun onAction(action: UiAction) {
        updateState { it.copy(count = it.count + 1) }
        sendEffect(PhotoEffect.Saved)
        launch { /* 失败会进 onError，不会裸抛 */ }
    }
}
```

**网络**：从容器取 `NetworkClient`，只调 `get` / `post`，失败不抛异常。

```kotlin
val result: ApiResult<Model> = networkClient.get("models", query = mapOf("page" to "1"))
when (result) {
    is ApiResult.Success -> render(result.data)
    is ApiResult.Failure -> toast(result.error.describe())
}
```

**日志**：注入 `LogUtil` 后调用 `logUtil.d { "..." }`（传 lambda，关闭时零开销），输出形如 `MainActivity.kt:42 D ...`，
`sk-` 开头的密钥与 `Authorization` 头自动脱敏。`LogUtil` 由 Koin 提供单例，不再是全局 object。

**应用入口**：`FoolproofApp : BaseApplication()`，需要额外模块时覆写 `koinModules()`。

> 约定：`:base` 不含 UI、不申请权限、不发起任何请求；网络客户端全项目唯一，业务模块不得自行创建。

## AGP 9 约定

本项目基于 AGP 9，写法与网上多数 AGP 8 时代的教程不同，改动构建脚本时请注意：

1. **不要引入 `org.jetbrains.kotlin.android` 插件** — AGP 9 内置 Kotlin 支持，只需 `org.jetbrains.kotlin.plugin.compose`。
2. **`compileSdk` 是块式写法**：`compileSdk { version = release(37) }`，而非 `compileSdk = 37`。
3. **R8 规则放在 `app/src/main/keepRules/`**，目录下的文件会全部合并；不存在 `proguard-rules.pro`。
4. **release 混淆通过 `optimization {}` 配置**：

   ```kotlin
   buildTypes {
       release {
           optimization {
               enable = true
               packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
           }
       }
   }
   ```

5. release 构建**尚未配置签名**，发布前需补充 `signingConfig`。

## 开发流程

本项目已初始化 [spec-kit](https://github.com/github/spec-kit)（1.2.2，DSH 集成），采用规格驱动开发：

```
constitution → specify → plan → tasks → implement
```

- 宪法：[`.specify/memory/constitution.md`](.specify/memory/constitution.md)（**v1.2**，2026-10-20，最高约束）
- 原始需求记录：[`docs/requirements.md`](docs/requirements.md)（已被宪法取代，仅作历史留存）
- 特性规格：`specs/<feature>/`（尚未创建）
- 完整流水线定义：[`.specify/workflows/speckit/workflow.yml`](.specify/workflows/speckit/workflow.yml)

新功能从 `/speckit.specify` 开始，描述需求即可，无需手写规格文档结构。**动手前先读宪法**：
技术栈、模块边界、MVI 约定、权限与隐私红线都在里面，违反即退回。

## 提交规范

采用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <subject>

feat: 新增 XX 功能
fix: 修复 XX 问题
docs: 更新文档
refactor: 重构
test: 补充测试
chore: 构建/依赖/配置调整
```

- 分支：`main` 为稳定基线，功能开发走 `feature/<name>`
- 提交前确保 `./gradlew :app:testDebugUnitTest` 通过

## 许可证

尚未指定。若需开源，请在此补充 License 并添加 `LICENSE` 文件。

## 文件转 PDF（已实现，图片 / 文本·Markdown）

底部第 3 个 tab，模块 `:features:file-conversion`；导航由 `:common` 的 `FoolproofTabHost` 封装
（宪法 §2 v1.17：轻量 tab host，禁止业务模块自写导航）。

- **图片 → PDF**：Photo Picker 多选（≤50 张），缩略图可删除/上移；A4 竖版逐页居中，保持比例不变形；
  解码前采样（长边 ≤2048）+ EXIF 方向修正；HEIC 在 API 24–27 明确提示并跳过（宪法 §3.3）
- **文本 / Markdown → PDF**：SAF 选取（≤2MB），自研极简 Markdown（标题 / 段落 / 列表 / 粗斜体 /
  行内码 / 代码块 / 分隔线）；CJK 逐字 + 拉丁按词折行，超长串强制断行，分页不切断单行
- **生成**：原生 `PdfDocument`（零第三方 PDF 库）；图片页位图、文本页矢量文字可选中复制
- **导出**：`files/pdf/` + FileProvider（`<包名>.files`）；API ≥ 29 可存「下载/Foolproof」，零写权限
- **清理**：新任务前删旧产物、失败/取消删半成品、启动清残留（与视频模块同一模式）