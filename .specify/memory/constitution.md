# 项目宪法（Project Constitution）

> 本文档是本项目的最高约束。代码、规格、评审与发布说明都不得与本文档冲突。
> 如需变更，必须先修改本文档并递增版本号、写明理由；不能先改代码再补文档。
>
> 当前版本：**v1.20（2026-10-23，激励解锁）**
> 校准目标：门控从「自动插屏」改为「每日免费 5 次 + 用户主动看激励视频 +5 次」，
> 免费额度 0 点重置、激励余额跨天保留。

***

## 1. 项目定位

### 1.1 做什么

纯本地 Android 媒体工具箱，不依赖自建后端。当前 3 个底部 tab：

| Tab | 能力 |
| --- | --- |
| 图片 | 图片压缩、重复照片清理 |
| 视频 | 视频压缩、视频拼接、视频裁剪 |
| 转换 | 图片/文本 → PDF、PDF 工具（合并/拆分/旋转/页面管理/压缩/转图片） |

另有：

- **系统分享入口**：其他 App 分享图片/视频/PDF 后可直达对应能力；
- **本地账号与设置**：手机号验证码登录、密码登录、语言/主题设置、退出登录；
- **广告**：Meta Audience Network 与穿山甲（Pangle）二选一，按构建开关 `foolproof.adSdk` 打包；

### 1.2 不做什么（红线）

- ❌ 不写后端、不买服务器、不做真实短信/账号体系；登录是**本地模拟**，不产生服务端账号；
- ❌ 不做云同步、相册备份、社交分享社区；
- ❌ 不上传用户图片、视频、PDF 内容；
- ❌ 不做视频剪辑/多轨/变速/转 GIF 等压缩、拼接、裁剪以外的视频处理；
- ❌ 不内置任何第三方 API Key，不代理、不转卖 API；
- ❌ 不引入 FFmpeg、LibreOfficeKit、iText 等重量级/许可风险依赖；
- ❌ 不申请 `WRITE_EXTERNAL_STORAGE`。

> 语音聊天（Whisper/GPT/TTS）原为规划功能，**当前未实现、不进入 v1**。将来若做，必须单独走 spec-kit 并重新评估网络、隐私与依赖。

***

## 2. 技术栈（锁定）

| 类别 | 选型 | 备注 |
| --- | --- | --- |
| 语言 | Kotlin | 禁止新增 Java 业务代码 |
| UI | Jetpack Compose + Material3 | 不写 XML 业务布局 |
| DI | Koin 4.2.2 | 不用 Hilt/Dagger |
| 架构 | MVI（StateFlow + Channel） | 见 §4 |
| 本地存储 | **AndroidX DataStore Preferences 1.1.7** | 统一封装在 `:common` 的 `LocalStore`；不再使用 SharedPreferences/Room |
| 图片压缩 | 自研 `BitmapFactory` + `Bitmap.compress` | `androidx.exifinterface` 仅用于方向/元数据 |
| 图片加载 | Coil 3.6.3 | 业务模块不直接 new ImageLoader |
| 视频 | Media3 Transformer 1.11.1 + media3-effect | 转码/缩放/裁剪/拼接，不引 FFmpeg |
| PDF 能力 | **PDFBox-Android 2.0.27.0** | PDF 合并/拆分/旋转/页面/压缩/转图片；生成 PDF 仍用原生 `PdfDocument` |
| 相机 | CameraX 1.4.2 | 拍照转 PDF |
| 广告 | Meta Audience Network 6.22.0（海外）<br>穿山甲 GroMore 7.8.1.4（国内） | 分别封装在 `:ads:meta` / `:ads:pangle`；构建开关 `foolproof.adSdk` 二选一，单个产物只含一个 SDK；feature 只依赖 `:base` 的 `AdGate` |
| 网络 | Ktor Client（base 封装） | 当前无业务网络功能；为将来保留，禁止 feature 自建客户端 |
| 异步 | Coroutine + Flow | 禁止 RxJava |
| 测试 | JUnit4 + coroutines-test + koin-test | 仅 test 配置 |
| SDK | minSdk 24 / compileSdk 37 / targetSdk 37 | 工程现状 |

***

## 3. 模块结构

```
:app                         // 导航、tab、抽屉、登录/设置页、依赖组装；不写业务状态机
:base                        // MVI 基类、Koin、日志、AdGate 接口
:common                      // 通用导航、LocalStore、SuccessDialog、通用工具
:ads:api                     // 广告抽象：AdSdk、横幅/开屏槽、门控策略（无具体 SDK）
:ads:meta                    // Meta Audience Network 实现
:ads:pangle                  // 穿山甲（国内）实现
:features:image-compression  // 图片压缩
:features:duplicate-cleanup  // 重复照片扫描/分组/删除
:features:video-compression  // 视频压缩/拼接/裁剪
:features:file-conversion    // 图片/文本→PDF、PDF 工具
```

依赖方向：

```
app → base, common, features/*, ads:api + 选中的一个 ads provider
feature → base, common
ads → base
common → base
base → 无业务依赖
feature 之间禁止互相依赖
```

`:common` 已存在，职责包括：

- `FoolproofTabHost`：底部 tab 导航；
- `LocalStore`：DataStore 唯一读写入口；
- `SuccessDialog`：带预览的成功弹层；
- 跨 feature 的纯工具（字节格式化、Activity 查找等）。

`:app` 负责：

- 3 个 tab 的组装与切换；
- 左侧用户抽屉、设置全屏页、登录页；
- 分享入口分发；
- 广告初始化与展示（只依赖 `foolproof.adSdk` 选中的那一个 provider）；
- **不写**压缩/分组/PDF 转换等业务规则。

***

## 4. MVI 规范

1. ViewModel 继承 `BaseViewModel<S, E>`；
2. State 为不可变 data class，Effect 为 sealed interface；
3. 状态用 `MutableStateFlow`，副作用用 `Channel`；
4. ViewModel 不持有 View/Activity；Context 用 application context；
5. 业务协程走基类 `launch {}`，禁止裸抛到 `viewModelScope`；
6. UI 单向 `dispatch(Action)`；一次性事件（保存成功、分享失败）走 Effect。

***

## 5. 本地存储、登录与设置

### 5.1 存储

- 唯一实现：`:common` 的 `LocalStore`，底层 DataStore Preferences；
- 文件：`app_data.preferences_pb`；
- 键必须有类型化读写封装，禁止在 UI 直接操作 DataStore；
- 旧的 SharedPreferences 文件在启动时清理，**不做数据迁移**（产品已确认旧数据无需保留）。

### 5.2 登录（本地模拟）

- 默认入口：手机号 + 验证码；
- 验证码固定 **`0000`**，密码固定 **`admin`**；
- 获取验证码后 **30 秒倒计时**，倒计时结束可重发；
- 密码登录是独立页面，不是 tab；
- **没有独立注册页**：首次验证成功即视为登录/自动注册；
- 登录态持久化在 DataStore；
- 不请求后端、不发真实短信；UI 不要求标注“demo”，但代码与文档必须明确是本地模拟。

### 5.3 设置

- 从左侧抽屉进入**全屏设置页**，不是 Dialog；
- 语言、主题（跟随系统/浅色/深色）持久化；
- 退出登录固定在设置页底部，仅登录后显示；
- 切换语言后用本地化 `ContextWrapper`，禁止把 `LocalContext.current` 直接强转 Activity。

***

## 6. UI 规范

- 内容区左右统一 **16dp**，由 `FoolproofTabHost` 统一施加；feature 不再重复加横向 16dp；
- 底部导航 3 项；选中项使用实心 `primary` 指示器 + `onPrimary` 图标 + `primary` 加粗文字；
- 成功反馈用 `SuccessDialog`（标题、摘要、预览、分享/保存/关闭），不用一闪而过的 Toast；
- 页面文案全部走 `strings.xml`，至少覆盖 zh/en/ja/fr/de/km；
- 列表滚动容器由 `:app` 或明确的单页容器持有；feature Section 不自带嵌套滚动；
- 首页压缩结果行必须是独立 lazy item，禁止把全部结果塞进同一个 item。

***

## 7. 重复照片清理

### 7.1 分组

- 8×8 dHash + 汉明距离；
- 距离 **≤5** 直接候选；
- 距离 **6–12** 时，必须再比较 9×8 灰度相关性，**≥0.92** 才归组；
- 低信息量（纯色/近纯色）图片不能只靠哈希，必须叠加宽高比与体积保护；
- 分组以“代表比较 + 完全哈希优先”为准，避免链式合并。

### 7.2 展示

- 每个重复组一个圆角卡片；
- 固定 3 列、单元格 1:1，最后一行补空位，禁止最后一张拉满整行；
- 缩略图使用 `ContentScale.Fit` 完整显示，禁止为了填满而裁掉内容；
- 卡片带组号、张数、可释放体积、描边/阴影，保证相邻组可区分；
- 缩略图必须用系统缩略图或采样解码，并做内存缓存，禁止为格子解码原图。

### 7.3 删除

- 绝不自动删除；默认每组预选“除最大一张以外的其余项”，用户可改选；
- 删除必须二次确认，写明张数与总大小；
- API ≥ 30 必须用 `MediaStore.createTrashRequest()`（回收站），禁止 `createDeleteRequest()`；
- API 24–29 走 SAF，明确提示不可恢复；未授权时删除置灰；
- 只申请读媒体权限，不申请写权限。

***

## 8. PDF 与文件转换

### 8.1 生成 PDF

- 图片/文本 → PDF 用原生 `PdfDocument`；
- A4 竖版、保持比例、EXIF 纠正、长边上限；
- 相机用 CameraX；
- 输出到应用私有目录，可分享/存下载。

### 8.2 PDF 工具（PDFBox-Android）

| 模式 | 行为 |
| --- | --- |
| 合并 | 多个 PDF 按顺序合并 |
| 拆分 | 页码范围（如 `1-3,5`）导出 |
| 旋转 | 整份 90/180/270 |
| 页面 | 缩略图列表，调整顺序/删除页后重写 |
| 压缩 | PdfRenderer 栅格化 + JPEG 质量，PDFBox 重建 |
| 转图片 | 每页渲染 JPEG，可预览、全量分享、存下载 |

- 压缩是**有损**的，文字层会丢失，UI 必须提示；
- 加密 PDF 当前不支持，必须明确失败而不是静默；
- PDF 转图片输出到 `pdf_images/`，FileProvider 必须配置该路径。

***

## 9. 视频

- 压缩：档位/目标体积、裁剪时间、进度/取消；
- 拼接：2–10 段，可排序/删除，重复视频自动跳过；
- 裁剪：只保留 start–end，最小 0.5 秒；
- 三者共用 `VideoTabScreen`，用 `HorizontalPager` 左右滑动；点击胶囊与滑动双向同步；
- 视频页的抽屉边缘手势只允许在“压缩”页（24dp 左侧拦截层），拼接/裁剪页横向手势归 Pager；
- 视频页抽屉内置手势关闭，但必须有显式遮罩/返回关闭，不能卡死；
- 输出统一走 FileProvider 分享、MediaStore 存相册，不申请写权限。

***

## 10. 系统分享入口

- 支持 `ACTION_SEND` / `ACTION_SEND_MULTIPLE`；
- 图片 → 图片 tab，自动载入并开始压缩；
- 视频 → 视频 tab 的压缩页；
- PDF → 转换 tab 的 PDF 工具；
- 多选复用批量流程；
- 分享进来的 PDF 必须复制到缓存再处理，不能假设 Uri 授权长期有效；
- 读取失败必须有明确错误提示，不能静默。

***

## 11. 权限、隐私与广告

- `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED`（Android 13+）、旧版 `READ_EXTERNAL_STORAGE`（maxSdk 32）；
- `INTERNET` / `ACCESS_NETWORK_STATE` / `AD_ID` 仅供广告；
- 不申请 `WRITE_EXTERNAL_STORAGE`、定位、通讯录、短信；
- 广告 SDK 的 AAR 会带入 `WRITE_EXTERNAL_STORAGE`、`READ_PHONE_STATE`、定位、通讯录、短信、
  `REQUEST_INSTALL_PACKAGES`、`QUERY_ALL_PACKAGES` 等权限，MUST 在 `:app` 清单用
  `tools:node="remove"` 剔除，不得随依赖悄悄进入产物；
- 穿山甲在未配置 appId/代码位、或未获得隐私同意前 MUST NOT 初始化（当前实现：未配置即不 init，
  且初始化时关闭位置/电话状态/应用列表/写外部存储采集）；
- 广告门控 MUST 采用「每日免费 + 用户主动激励」：每个功能每天免费 5 次（本地时区 0 点重置、
  不累计），用完后由用户主动看激励视频 +5 次（可叠加、跨天保留）；**禁止自动播放激励视频**；
- 广告不可用（加载失败/无填充/超时）MUST 放行，不能把核心功能锁死；
- 图片/视频/PDF 处理全程本地；广告 SDK 可能联网，但**不上传用户文件内容**；
- 隐私政策必须说明广告 SDK 的数据处理与本地登录的模拟性质；
- 分享入口的临时读权限只用于当次处理。

***

## 12. 一票否决项

- ❌ feature 直接 new Ktor client / 写死 URL；
- ❌ ViewModel/工具类里 `startActivity` / `Toast`；
- ❌ feature 之间互相 import；
- ❌ 业务代码重复粘贴超过 2 次；
- ❌ 引入第 2 节以外的框架（先改宪法）；
- ❌ release 保留 `Log.d` / `printStackTrace`；
- ❌ 用 Java 写新业务；
- ❌ 在 `:app` 写业务状态机或转换规则；
- ❌ 新增功能没有 spec/plan/tasks（见 §13）。

***

## 13. Spec-kit 工作流（治理规则）

- **新增功能、行为变更、依赖变更**：必须依次有 `spec.md` → `plan.md` → `tasks.md`，再实现；
- **纯 bugfix、性能、文案、样式**：可以快修，但必须在提交信息与 `docs/` 中说明，不强制补 spec；
- 已上线但缺 spec 的功能，按 `specs/006+` 追溯补齐，状态标为 `Implemented (retro-spec)`；
- 修改宪法必须递增版本并写 changelog；规格与宪法冲突时，先改宪法，不能只改代码；
- 每个 spec 的需求必须可测试，验收场景必须能独立验证；
- 宪法 > spec > plan > tasks > code，冲突时以高优先级为准。

***

## 14. 版本记录

- v1.0–v1.17（2026-10-20 至 2026-10-21）：完成 base / 图片压缩 / 重复清理 / 视频压缩 / 文件转 PDF，
  并逐步引入 Compose、Koin、Media3、广告、分享入口等；详细历史见 Git 提交。
- **v1.18（2026-10-22，追溯校准）**：
  - 项目范围改为当前 3 tab 的实际能力，移除“未实现的语音聊天”和“真实账号体系”表述；
  - 本地存储由 Room/EncryptedSP 更正为 DataStore；
  - PDF 工具由“仅原生 PdfDocument”更正为 PDFBox-Android；
  - 补充视频拼接/裁剪、系统分享入口、本地模拟登录、设置页、全局 16dp、底部导航选中态；
  - 重复清理补充灰度相关性兜底、分组卡片、3 列正方形与 `ContentScale.Fit`；
  - 新增 §13 spec-kit 治理边界；
  - 修正旧版“4 tab / 语音聊天”等与代码不一致的描述。
- **v1.19（2026-10-22，广告 provider 可切换）**：
  - 新增 `:ads:api`（广告抽象）、`:ads:meta`、`:ads:pangle` 三个模块，广告实现从 `:app` 迁出；
  - 登记穿山甲 GroMore 7.8.1.4（官方 Maven `com.pangle.cn:mediation-sdk`，聚合广告位 ID 以 1 开头，
    初始化必须 `useMediation`），由构建开关 `foolproof.adSdk`（默认 `meta`）决定单个产物只含一个 SDK；
  - `AdGate` 接口保持不变，feature 模块零改动；门控策略下沉到 `:ads:api` 的 `CountingAdGate`，
    新增 5 秒超时兜底；
  - 补充广告 SDK 敏感权限剔除、穿山甲初始化合规约束与开屏素材明文流量要求。
- **v1.20（2026-10-23，激励解锁）**：
  - 门控由「自动弹插全屏」改为「每日免费 5 次」+「用户主动看激励视频 +5 次」：
    免费额度按本地时区 0 点重置、不累计；激励余额可叠加、跨天不清零；
  - `AdGate` 改为 `available / isLocked / requestUnlock`，5 个 feature 的按钮在锁定态
    切换为「看广告解锁 5 次」并弹确认框；禁止自动播放激励视频；
  - 穿山甲使用 `loadRewardVideoAd`（聚合广告位 `104722138`），Meta 使用 `RewardedVideoAd`；
  - 广告失败/无填充/8 秒超时放行当前一次且不扣额度，同一功能 60 秒内不再等待。
