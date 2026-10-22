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

广告 SDK 默认打包 Meta；出穿山甲（国内）包时追加属性（PowerShell 需要引号）：

```powershell
.\gradlew.bat :app:assembleRelease "-Pfoolproof.adSdk=pangle"
```

属性只影响 `:app` 依赖的那一个 provider，命令与产物路径不变。

仪器化测试只跑单个设备时设置 `ANDROID_SERIAL=emulator-5554`。

## 体积

| 包 | 大小 |
| --- | --- |
| debug APK | ~33 MB（含 Compose tooling；pangle 构建约 35 MB） |
| release APK | ~13.3 MB（meta；pangle 发布前重新记录） |

已做的优化：

- 排除 BouncyCastle 后量子参数资源（约 4 MB）；
- 库语言资源限制为 6 种；
- release 使用 AGP 9 的 R8 优化。

可继续优化：移除未使用的 Ktor/BouncyCastle、按需下载大模型、Play AAB 拆分。

## 签名

release 已配置签名，密钥与密码放在项目根目录：

- `keystore/foolproof-release.jks`
- `keystore.properties`（**已 gitignore，不要提交**）

`keystore.properties` 格式见 `keystore.properties.example`：

```properties
storeFile=keystore/foolproof-release.jks
storePassword=...
keyAlias=foolproof
keyPassword=...
```

- 丢失 keystore 将无法更新已上架应用，务必备份到密码管理器/离线介质；
- 首次上传 Play 后，这个 keystore 就是上传密钥；启用 Play App Signing 后由 Google 保管发布密钥；
- CI 环境不要把密码写进仓库，可用环境变量/Secrets 生成 `keystore.properties`。

构建：

```bash
./gradlew :app:assembleRelease   # 已签名 APK
./gradlew :app:bundleRelease     # 已签名 AAB（Play 用）
```

校验：

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

证书 SHA-256 会随构建固定；更换 keystore 会导致老用户无法覆盖安装。

## 测试

- `:base`、`:features:*` 均有 JVM 单测；
- PDFBox 引擎有仪器化测试（合并 4 页、拆分 2 页、旋转、页序重写、压缩）；
- 真机回归清单：视频目标体积、PDF 页面/压缩、加密 PDF、低存储、大文件。
