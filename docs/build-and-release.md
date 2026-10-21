# 构建与发布

## 环境

- JDK 17+（本机使用 JetBrains Runtime 25）
- Android SDK Platform 37 + Build-Tools
- Android Studio（支持 AGP 9.4）
- `local.properties` 指向本机 SDK，**不要提交**

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew :app:assembleDebug` | debug APK |
| `./gradlew :app:installDebug` | 安装到设备 |
| `./gradlew :app:testDebugUnitTest` | JVM 单测 |
| `./gradlew :features:video-compression:testDebugUnitTest` | 视频模块单测 |
| `./gradlew :features:file-conversion:connectedDebugAndroidTest` | PDF 引擎仪器化测试 |
| `./gradlew :app:assembleRelease` | release APK（未签名） |
| `./gradlew :app:bundleRelease` | release AAB |

仪器化测试只跑单个设备时设置 `ANDROID_SERIAL=emulator-5554`。

## 体积

| 包 | 大小 |
| --- | --- |
| debug APK | ~33 MB（含 Compose tooling） |
| release APK | ~13.3 MB |

已做的优化：

- 排除 BouncyCastle 后量子参数资源（约 4 MB）；
- 库语言资源限制为 6 种；
- release 使用 AGP 9 的 R8 优化。

可继续优化：移除未使用的 Ktor/BouncyCastle、按需下载大模型、Play AAB 拆分。

## 签名

release 尚未配置签名。本地测试可用 debug keystore：

```bash
apksigner sign --ks ~/.android/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out app-release-signed.apk app-release-unsigned.apk
```

正式发布请配置自己的 keystore，不要提交到仓库。

## 测试

- `:base`、`:features:*` 均有 JVM 单测；
- PDFBox 引擎有仪器化测试（合并 4 页、拆分 2 页、旋转、页序重写、压缩）；
- 真机回归清单：视频目标体积、PDF 页面/压缩、加密 PDF、低存储、大文件。
