package com.foolproof.features.compression.domain

/**
 * 读取选中图片的元数据（名称、体积、MIME、宽高）。
 *
 * 抽成接口是因为它必须访问 `ContentResolver` 与 `BitmapFactory`，在 JVM 单测里跑不了；
 * ViewModel 依赖它，测试时替换为假实现。
 */
interface ImageMetadataReader {

    /**
     * @param ids 图片的字符串标识（实际是 content Uri 的字符串形式）
     * @return 成功读取的图片；单个失败会被跳过，不影响其余
     */
    suspend fun read(ids: List<String>): List<SourceImage>
}
