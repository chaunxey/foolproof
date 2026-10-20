# Foolproof

> **状态：规格阶段** — 宪法已定稿（v1.2），工程仍是 Android Studio 新建的 Compose 模板，模块骨架尚未搭建。

**简介**：纯本地的图片 / 文件处理工具 App。所有处理都在设备上完成，不依赖后端、不上传用户文件；
唯一需要联网的是「AI 语音聊天」，使用用户自己填写的 API Key，费用自负。

**目标功能**：

1. 图片压缩（Compressor，输出 WebP / JPEG）
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
├── docs/requirements.md            # 原始需求记录（已由宪法取代）
├── specs/001-base-module/          # 本 feature 的 spec / plan / tasks
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
:app                             # 底部导航壳，不写业务            ✅ 已建
:base                            # MVI 基类、Koin、Ktor 封装、日志  ✅ 已建
:common                          # 导航封装、common/api、通用工具    ⏳ 待建
:features:image-compression      # 功能 1                          ⏳ 待建
:features:duplicate-cleanup      # 功能 2（UI 与 image-compression 同页）
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

