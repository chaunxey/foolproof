# Foolproof

> **状态：脚手架阶段** — 目前是基于 Android Studio 新建 Compose 模板的空壳项目，尚无业务代码。

<!-- TODO: 用两三句话说明 Foolproof 是什么、解决什么问题、面向谁。 -->
**简介**：待补充。

<!-- TODO: 首个可交付版本要做的核心能力，按重要性排序。 -->
**目标功能**：待补充。

---

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | 使用 AGP 9 的新版 DSL（见下方「AGP 9 约定」） |
| Gradle | 9.6.0 | 已启用 Configuration Cache |
| Kotlin | 2.2.10 | 仅应用 Compose 编译器插件，Kotlin 编译由 AGP 内置支持 |
| Jetpack Compose | BOM 2026.02.01 | Material 3 + 动态取色（Android 12+） |
| compileSdk / targetSdk | 37 | — |
| minSdk | 24 | Android 7.0 |
| JVM Target | Java 11 | 编译期 JDK 为 JetBrains Runtime 25 |

依赖版本统一在 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 中以 Version Catalog 管理，**不要在 `build.gradle.kts` 里硬编码版本号**。

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
├── app/
│   ├── build.gradle.kts          # 模块配置（AGP 9 DSL）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/foolproof/
│       │   │   ├── MainActivity.kt      # 唯一入口 Activity（Compose）
│       │   │   └── ui/theme/            # Material 3 主题：Color / Type / Theme
│       │   ├── keepRules/rules.keep     # R8 混淆规则（AGP 9 新位置）
│       │   └── res/                     # 资源与图标
│       ├── test/                        # 本地单元测试
│       └── androidTest/                 # 仪器化测试
├── gradle/
│   ├── libs.versions.toml        # 依赖版本目录
│   └── wrapper/                  # Gradle Wrapper 9.6
├── .specify/                     # spec-kit 脚手架：模板、脚本、工作流
├── .dsh/skills/                  # 本项目的 speckit 命令技能
├── .gitattributes                # 换行符规范（gradlew 保持 LF）
└── build.gradle.kts              # 根构建脚本
```

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

- 宪法：[`.specify/memory/constitution.md`](.specify/memory/constitution.md)（当前仍为模板占位，需先撰写）
- 特性规格：`specs/<feature>/`（尚未创建）
- 完整流水线定义：[`.specify/workflows/speckit/workflow.yml`](.specify/workflows/speckit/workflow.yml)

新功能从 `/speckit.specify` 开始，描述需求即可，无需手写规格文档结构。

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
