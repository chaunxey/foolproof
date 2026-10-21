package com.foolproof.features.video

import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.last
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成一段**带音轨**的合成视频，用于验证预览播放是否有声音。
 *
 * 背景：`screenrecord` 默认不录声音，此前所有测试素材都是无声的，
 * 因此"播放没声音"无法判断是素材问题还是播放器问题。这里用 MediaCodec
 * 现场合成「纯色画面 + 440Hz 正弦音」，两轨混流成 mp4。
 *
 * 产物写到应用外部私有目录，测试后用 adb 取出，再推到相册里供手工验证。
 */
@RunWith(AndroidJUnit4::class)
class AudioSampleGeneratorTest {

    private data class Sample(val data: ByteArray, val ptsUs: Long)

    @Test
    fun generateVideoWithAudioTone() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temp = File(context.cacheDir, "audio_sample.mp4")
        temp.delete()

        val durationMs = 5_000
        val (audioFormat, audioSamples) = encodeAudioTone(durationMs)
        encodeVideoWithAudio(temp, audioFormat, audioSamples, durationMs)

        assertTrue("样本应包含数据", temp.length() > 0)
        assertTrue("音频样本不应为空", audioSamples.isNotEmpty())

        // 通过 MediaStore 落到 Movies/（API 29+ 无需写权限），相册与选择器即可看到
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "audio_sample.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        assertTrue("MediaStore 插入失败", uri != null)
        context.contentResolver.openOutputStream(uri!!)!!.use { out ->
            temp.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        temp.delete()
    }

    /** 440Hz 正弦音 → AAC。返回编解码器输出的格式与全部编码样本。 */
    private fun encodeAudioTone(durationMs: Int): Pair<MediaFormat, List<Sample>> {
        val sampleRate = 44_100
        val channels = 1
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val totalFrames = durationMs.toLong() * sampleRate / 1000
        var frameIndex = 0L
        var presentationUs = 0L
        val info = MediaCodec.BufferInfo()
        val samples = mutableListOf<Sample>()
        var outputFormat: MediaFormat? = null

        while (true) {
            val inIndex = codec.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val buffer = codec.getInputBuffer(inIndex)!!
                buffer.clear()
                if (frameIndex < totalFrames) {
                    val chunk = minOf(1024L, totalFrames - frameIndex).toInt()
                    val pcm = ByteBuffer.allocate(chunk * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until chunk) {
                        val t = (frameIndex + i).toDouble() / sampleRate
                        // 振幅 0.3，避免削波
                        pcm.putShort((sin(2 * PI * 440.0 * t) * 0.3 * Short.MAX_VALUE).toInt().toShort())
                    }
                    buffer.put(pcm.array())
                    val ptsUs = frameIndex * 1_000_000L / sampleRate
                    codec.queueInputBuffer(inIndex, 0, chunk * 2, ptsUs, 0)
                    frameIndex += chunk
                    presentationUs = ptsUs
                } else {
                    codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat

                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                else -> if (outIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outIndex)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        encoded.get(bytes)
                        samples += Sample(bytes, info.presentationTimeUs)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        codec.stop()
                        codec.release()
                        return (outputFormat ?: format) to samples
                    }
                }
            }
        }
    }

    /** 视频轨（纯色）+ 已编码好的音频轨混流。 */
    private fun encodeVideoWithAudio(
        output: File,
        audioFormat: MediaFormat,
        audioSamples: List<Sample>,
        durationMs: Int,
    ) {
        val width = 640
        val height = 480
        val frameRate = 30

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var audioTrack = -1
        var started = false
        var audioCursor = 0

        val info = MediaCodec.BufferInfo()
        val frameCount = durationMs * frameRate / 1000
        val frameSize = width * height * 3 / 2
        val yuv = ByteArray(frameSize).apply {
            java.util.Arrays.fill(this, 0, width * height, 100.toByte())      // 灰
            java.util.Arrays.fill(this, width * height, frameSize, 128.toByte())
        }

        var inputIndex = 0
        var outputIndex = 0

        while (outputIndex <= frameCount) {
            if (inputIndex < frameCount) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.getInputBuffer(inIndex)!!.apply {
                        clear()
                        put(yuv)
                    }
                    codec.queueInputBuffer(
                        inIndex,
                        0,
                        yuv.size,
                        inputIndex * 1_000_000L / frameRate,
                        0,
                    )
                    inputIndex++
                }
            } else if (inputIndex == frameCount) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputIndex++
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoTrack = muxer.addTrack(codec.outputFormat)
                    audioTrack = muxer.addTrack(audioFormat)
                    muxer.start()
                    started = true
                }

                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0

                    if (info.size > 0 && started) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer.writeSampleData(videoTrack, encoded, info)

                        // 让音频轨的样本时间戳不超过视频当前时间，保证交错写入
                        while (audioCursor < audioSamples.size &&
                            audioSamples[audioCursor].ptsUs <= info.presentationTimeUs
                        ) {
                            val sample = audioSamples[audioCursor]
                            val buf = ByteBuffer.wrap(sample.data)
                            val audioInfo = MediaCodec.BufferInfo().apply {
                                set(0, sample.data.size, sample.ptsUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                            }
                            muxer.writeSampleData(audioTrack, buf, audioInfo)
                            audioCursor++
                        }
                        outputIndex++
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        // 收尾：写完剩余的音频样本
        while (started && audioCursor < audioSamples.size) {
            val sample = audioSamples[audioCursor]
            val buf = ByteBuffer.wrap(sample.data)
            val audioInfo = MediaCodec.BufferInfo().apply {
                set(0, sample.data.size, sample.ptsUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
            }
            muxer.writeSampleData(audioTrack, buf, audioInfo)
            audioCursor++
        }

        codec.stop()
        codec.release()
        if (started) muxer.stop()
        muxer.release()
    }

    /**
     * 压缩带音轨的样本，断言输出仍然含音频轨。
     *
     * 这一条防的是「压缩把声音弄丢了」——用户感知上比画质更严重。
     */
    @Test
    fun compressionKeepsTheAudioTrack(): Unit = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "src_audio.mp4")
        val output = File(context.cacheDir, "out_audio.mp4")
        source.delete()
        output.delete()

        val (audioFormat, audioSamples) = encodeAudioTone(3_000)
        encodeVideoWithAudio(source, audioFormat, audioSamples, 3_000)

        val compressor = com.foolproof.features.video.data.Media3VideoCompressor(
            context,
            com.foolproof.base.log.LogUtil(enabled = false),
        )
        val result = compressor.compress(
            com.foolproof.features.video.domain.VideoCompressRequest(
                sourceId = android.net.Uri.fromFile(source).toString(),
                outputPath = output.absolutePath,
                targetShortSide = 480,
                videoBitrateKbps = 1_200,
                frameRate = 30,
                trimStartMs = 0,
                trimEndMs = 3_000,
                hasAudio = true,
            ),
        ).last()

        assertTrue("压缩应成功：$result", result is com.foolproof.features.video.domain.VideoProgress.Success)
        assertTrue("输出应存在", output.length() > 0)

        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(output.absolutePath)
        var hasAudioTrack = false
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) hasAudioTrack = true
        }
        extractor.release()
        assertTrue("压缩后必须保留音频轨", hasAudioTrack)

        source.delete()
        output.delete()
    }
}