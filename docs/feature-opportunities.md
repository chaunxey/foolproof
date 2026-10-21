# 工具类功能机会评估（2026-10）

> 结论先行：优先做 **视频压到指定大小**，其次 **离线 PDF 工具箱**，**高棉语 OCR** 先小范围验证再投入。

## 总览

| 方向 | 需求信号 | 竞争 | 差异化 | 工作量（人日） | 风险 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| 视频压到指定大小 + 平台预设 | 强（WhatsApp/Telegram/Discord 上传限制） | 中高，但目标体积少见 | 高 | 5–8 | 中高（VBR 超目标） | P0 |
| 离线 PDF 合并/拆分/压缩 | 强（隐私、无上传） | 高，但本地完整工具少 | 中高 | 8–12 | 中（库体积/压缩质量） | P1 |
| 高棉语 OCR / 扫描 | 区域性强 | 低（无专门 Play 产品） | 高（区域） | 10–15 | 高（准确率） | P2（先验证） |

---

## 1. 视频压到指定大小 + 平台预设（P0）

**竞品**
- [Compressify](https://play.google.com/store/apps/details?id=com.compressify.video)
- [Video Compressor Reduce Size](https://play.google.com/store/apps/details?id=com.stcodesapp.video_compressor_video_converter)
- [视频压缩 - 缩小视频尺寸](https://play.google.com/store/apps/details?id=com.psoffritti.compress.video)
- [Image Resizer, Video Compress](https://play.google.com/store/apps/details?id=com.happyverse.vidcompress)
- CineZip（Apptopia 可查估计数据）

**空档**
多数只给“高/中/低”或分辨率档位，不保证输出体积；用户真实需求是「压到 WhatsApp 能发的 16MB」「Discord 10MB」「邮件 25MB」。

**关键词（需用工具验证量级）**
`compress video to 16mb`、`compress video for whatsapp`、`reduce video size to 10mb`、`video compressor target size`、`compress video for telegram`、`compress video for discord`、`video compressor for email`

**MVP**
- 目标体积 chips：10 / 16 / 25 / 50 MB + 自定义；
- 按 `目标字节 × 8 ÷ 时长` 估码率，单遍编码后校验，超出自动二遍；
- 结果页显示实际体积/分辨率/时长；
- 先支持单视频，批量后续。

**工作量拆解**
UI 2 天；码率估算 + 二遍 2–3 天；设备回归 1–2 天；埋点/文案 1 天。

**风险**
VBR 编码体积会漂移；不同厂商编码器差异；长视频二遍耗时翻倍。必须做「输出超限自动再压」。

---

## 2. 离线 PDF 工具箱（P1）

**竞品**
- [Echo PDF](https://www.appbrain.com/app/echo-pdf/com.echovita.pdftoolkit.echovitapdf)（工具箱型）
- [PDF Editor & Converter Tool](https://app.sensortower.com/overview/com.localtoolbox.app?country=US)
- 在线：iLovePDF、Smallpdf、PDF24（要上传）
- 开源/网页：[offline-pdf-tools](https://github.com/wallechfox/offline-pdf-tools)、[PaperKnifePlus](https://github.com/potatameister/PaperKnifePlus)

**空档**
Android 上「完全离线、无上传、无订阅」的合并/拆分/重排/压缩一体工具少；多数要么云、要么广告很重。

**关键词**
`merge pdf offline`、`split pdf`、`combine pdf files`、`compress pdf without upload`、`pdf compressor offline`、`reorder pdf pages`

**MVP**
- 合并、拆分（按页/范围）、重排、旋转、删除页；
- 图片转 PDF 复用现有能力；
- 压缩放到第二阶段（栅格化重渲染，画质/体积可调）。

**工作量拆解**
库选型 1 天；页面模型/缩略图 2 天；合并/拆分/重排 3 天；压缩 2–3 天；回归 2 天。

**风险**
PDF 库体积（PDFBox 约数 MB）与许可证；加密 PDF；压缩必然损画质，要明确提示。

---

## 3. 高棉语 OCR / 扫描（P2，先验证）

**竞品**
- Play 上几乎只有高棉语翻译器/词典，缺专门 OCR 扫描产品；
- 在线：[FastOCR Khmer](https://fastocr.org/khmer-ocr)；
- 系统层：Google Lens/Translate 可做部分识别，但不是文档工作流。

**空档**
「拍照 → 高棉语文本 → 复制/导出/可搜索 PDF」的本地闭环几乎没有。

**关键词**
`khmer ocr`、`khmer scanner`、`scan khmer document`、`khmer text recognition`、`khmer pdf ocr`、`khmer document scanner`

**MVP**
- 先做「拍照/选图 → 识别 → 复制/分享文本」，不做可搜索 PDF；
- Tesseract4Android + `khm.traineddata`，加透视校正/二值化预处理；
- 用真实高棉语文档测准确率，低于可用阈值就不投入。

**工作量拆解**
集成/模型 3 天；预处理 3 天；UI 2 天；准确率调优 3–5 天；真机回归 2 天。

**风险**
高棉文字形复杂、无空格分词，OCR 准确率是生死线；模型/APK 体积；低端机性能。

---

## 验证与决策门槛（2 周）

1. Play 搜索前 20 名，确认是否有「专门做这件事」的产品；
2. 翻头部竞品 1 星评论，统计「目标体积 / 离线 / 高棉语」相关抱怨；
3. 用 Google Keyword Planner、AppFigures/Sensor Tower 验证关键词量级；
4. 先发柬埔寨/泰国/越南小范围，观察：
   - 首次使用完成率；
   - 7 日留存；
   - 广告 eCPM 与人均展示；
   - 目标体积功能的二次使用率。
5. 决策：P0 直接做；P1 做完 P0 再做；P2 只有准确率达标且区域需求验证通过才做。

## 不建议碰

电池优化、内存加速、病毒扫描、隐藏摄像头检测——政策与信任风险高，且同质化严重。
