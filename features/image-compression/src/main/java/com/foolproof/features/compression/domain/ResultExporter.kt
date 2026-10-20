package com.foolproof.features.compression.domain

/**
 * 结果导出：分享与保存到相册。
 *
 * 实现负责平台差异（FileProvider、MediaStore），ViewModel 只关心成功与否。
 */
interface ResultExporter {

    /** 是否支持直接保存到相册（API ≥ 29 才为 true，宪法 §8 不允许为写权限破例）。 */
    fun canSaveToGallery(): Boolean

    /** @param filePath 压缩结果文件的绝对路径 */
    suspend fun share(filePath: String): Boolean

    /** @param filePath 压缩结果文件的绝对路径 */
    suspend fun saveToGallery(filePath: String): Boolean
}
