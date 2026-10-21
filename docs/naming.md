# 产物命名

保存到相册 / 下载的文件统一为：

```
Foolproof_{TYPE}_{ACTION}_{yyyyMMdd_HHmmss}[_{NN}].{ext}
```

| 功能 | 示例 |
| --- | --- |
| 图片压缩 | `Foolproof_IMG_COMP_20261021_161500.jpg` |
| 视频压缩 | `Foolproof_VID_COMP_20261021_161500.mp4` |
| 视频拼接 | `Foolproof_VID_MERGE_20261021_161500.mp4` |
| 生成 PDF | `Foolproof_PDF_CONV_20261021_161500.pdf` |
| PDF 合并 | `Foolproof_PDF_MERGE_20261021_161500.pdf` |
| PDF 拆分 | `Foolproof_PDF_SPLIT_20261021_161500.pdf` |
| PDF 旋转 | `Foolproof_PDF_ROTATE_20261021_161500.pdf` |
| PDF 页面重排 | `Foolproof_PDF_REORDER_20261021_161500.pdf` |
| PDF 压缩 | `Foolproof_PDF_COMP_20261021_161500.pdf` |

规则：

- 类型 `IMG` / `VID` / `PDF`；动作 `COMP` / `MERGE` / `SPLIT` / `ROTATE` / `REORDER` / `CONV`；
- 同目录重名追加 `_02`、`_03`；
- 只用 ASCII，不携带原文件名；
- 目录：`Pictures/Foolproof`、`Movies/Foolproof`、`Download/Foolproof`；
- 实现：`base/.../util/OutputNaming.kt`，单测 `OutputNamingTest`。

内部临时文件（如 `cache/pdf_capture/`、`cache/merge_*`）不要求命名规范，用户不可见。
