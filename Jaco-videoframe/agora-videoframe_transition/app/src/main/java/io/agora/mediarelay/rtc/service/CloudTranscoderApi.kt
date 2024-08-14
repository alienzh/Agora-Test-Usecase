package io.agora.mediarelay.rtc.service

import android.util.Base64
import android.util.Log
import com.blankj.utilcode.util.GsonUtils
import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.tools.LogTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

// 云端转码
class CloudTranscoderApi {

    companion object {
        private const val TAG = "CloudtranscoderApi"
        private const val BASE_URL = "https://api.sd-rtn.com"
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val appId: String get() = AgoraRtcEngineInstance.mAppId
    private val appKey: String get() = AgoraRtcEngineInstance.mAccessKey
    private val appSecret: String get() = AgoraRtcEngineInstance.mSecretKey

    @OptIn(ExperimentalCoroutinesApi::class)
    private val workerDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val okHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        builder.readTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(30L, TimeUnit.SECONDS)
            .connectTimeout(30L, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            builder.addInterceptor(CurlInterceptor(object : Logger {
                override fun log(message: String) {
                    Log.d("CurlInterceptor", message)
                }
            }))
        }
        builder.build()
    }

    private var tokenName = ""
    private var taskId = ""

    private val builderTokensUrl: String
        get() = "$BASE_URL/v1/projects/$appId/rtsc/cloud-transcoder/builderTokens"

    private val cloudTranscoderUrl: String
        get() = "$BASE_URL/v1/projects/$appId/rtsc/cloud-transcoder/tasks"

    private val basicAuth: String
        get() = "Basic ${Base64.encodeToString("$appKey:$appSecret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"

    // 获取云端转码资源
    private suspend fun reqCloudTranscoderToken() = withContext(workerDispatcher) {
        val requestBody = JSONObject().put("instanceId", System.currentTimeMillis().toString())
            .toString()
            .toRequestBody()
        val request = Request.Builder()
            .url(builderTokensUrl)
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuth)
            .post(requestBody)
            .build()
        val execute = okHttpClient.newCall(request).execute()
        if (execute.isSuccessful) {
            val _body = execute.body
                ?: throw RuntimeException("$builderTokensUrl error: httpCode=${execute.code}, httpMsg=${execute.message}, body is null")
            val token = GsonUtils.fromJson(_body.string(), ResponseCloudToken::class.java)
            tokenName = token.tokenName
            tokenName
        } else {
            throw RuntimeException("$builderTokensUrl error: httpCode=${execute.code}, httpMsg=${execute.message}")
        }
    }

    // 创建云端转码
    private suspend fun reqCreateCloudTranscoder(setting: TranscodeSetting) = withContext(workerDispatcher) {
        val tokenName: String = tokenName.ifEmpty { reqCloudTranscoderToken() }
        val url = "$cloudTranscoderUrl?builderToken=$tokenName"
        val jsonString = GsonUtils.toJson(dataMapFromSetting(setting))
        val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuth)
            .header("X-Request-ID", requestId)
            .post(requestBody)
            .build()
        val execute = okHttpClient.newCall(request).execute()
        if (execute.isSuccessful) {
            val _body = execute.body
                ?: throw RuntimeException("$url error: httpCode=${execute.code}, httpMsg=${execute.message}, body isnull")
            val resCloudTranscoder = GsonUtils.fromJson(_body.string(), ResponseCloudTranscoder::class.java)
            resCloudTranscoder.taskId
        } else {
            throw RuntimeException("$url error: httpCode=${execute.code}, httpMsg=${execute.message}")
        }
    }

    // 查询云端转码状态
    private suspend fun reqQueryCloudTranscoder() = withContext(workerDispatcher) {
        val tokenName: String = tokenName.ifEmpty { reqCloudTranscoderToken() }
        val url = "$cloudTranscoderUrl/$taskId?builderToken=$tokenName"

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuth)
            .header("X-Request-ID", requestId)
            .get()
            .build()
        val execute = okHttpClient.newCall(request).execute()
        if (execute.isSuccessful) {
            val _body = execute.body
                ?: throw RuntimeException("$url error: httpCode=${execute.code}, httpMsg=${execute.message}, body is null")
            GsonUtils.fromJson(_body.string(), ResponseCloudTranscoder::class.java)
        } else {
            throw RuntimeException("$url error: httpCode=${execute.code}, httpMsg=${execute.message}")
        }
    }

    private var sequenceId: Int = 100
    // 更新指定的云端转码
    private suspend fun reqUpdateCloudTranscoder(setting: TranscodeSetting) = withContext(workerDispatcher) {
        val tokenName: String = tokenName.ifEmpty { reqCloudTranscoderToken() }
        val url = "$cloudTranscoderUrl/$taskId?builderToken=$tokenName&sequenceId=${++sequenceId}&updateMask=services.cloudTranscoder.config"
        val jsonString = GsonUtils.toJson(dataMapFromSetting(setting))
        val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val request: Request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuth)
            .header("X-Request-ID", requestId)
            .patch(requestBody)
            .build()
        val execute = okHttpClient.newCall(request).execute()
        if (execute.isSuccessful) {
            execute.code in 200..299
        } else {
            throw RuntimeException("$url error: httpCode=${execute.code}, httpMsg=${execute.message}")
        }
    }

    // 销毁云端转码
    private suspend fun reqDeleteCloudTranscoder(taskId: String, tokenName: String) = withContext(workerDispatcher) {
        val tokenName: String = tokenName.ifEmpty { reqCloudTranscoderToken() }
        val url = "$cloudTranscoderUrl/$taskId?builderToken=$tokenName"

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuth)
            .header("X-Request-ID", requestId)
            .delete()
            .build()
        val execute = okHttpClient.newCall(request).execute()
        if (execute.isSuccessful) {
            val code = execute.code
            this@CloudTranscoderApi.tokenName = ""
            this@CloudTranscoderApi.taskId = ""
            execute.code in 200..299
        } else {
            throw RuntimeException("$url error: httpCode=${execute.code}, httpMsg=${execute.message}")
        }
    }

    private fun dataMapFromSetting(setting: TranscodeSetting): Map<String, Any> {
        val rtcToken = appId
        val audioInputs = mutableListOf<Map<String, Any>>()
        val videoInputs = mutableListOf<Map<String, *>>()
        val waterMarks = mutableListOf<Map<String, Any>>()
        val outputs = mutableListOf<Map<String, Any>>()

        setting.inputItems.forEach { item ->
            val audioRtcMap = mutableMapOf<String, Any>(
                "rtcChannel" to item.channel,
                "rtcToken" to rtcToken,
                "rtcUid" to item.uid,
            )
            val audioInput = mapOf("rtc" to audioRtcMap)

            val videoRtcMap = mutableMapOf<String, Any>(
                "rtcChannel" to item.channel,
                "rtcToken" to rtcToken,
                "rtcUid" to item.uid
            )
            val videoInput = mapOf(
                "rtc" to videoRtcMap,
                "placeholderImageUrl" to null,
                "region" to mapOf(
                    "x" to item.x,
                    "y" to item.y,
                    "width" to item.width,
                    "height" to item.height,
                    "zOrder" to 2,
                )
            )
            audioInputs.add(audioInput)
            videoInputs.add(videoInput)
        }

        val waterMark = mapOf(
            "imageUrl" to "https://doc.shengwang.cn/assets/images/showroom-demo-5b961e61358d0f2b19f155c340be25e9.png",
            "region" to mapOf(
                "x" to 0,
                "y" to 0,
                "width" to 120,
                "height" to 120,
                "zOrder" to 50,
            )
        )
        waterMarks.add(waterMark)

        setting.output.apply {
            val outRtcMap = mutableMapOf<String, Any>(
                "rtcChannel" to channel,
                "rtcToken" to rtcToken,
                "rtcUid" to uid,
            )
            val outPut = mapOf(
                "rtc" to outRtcMap,
                "audioOption" to mapOf(
                    "profileType" to "AUDIO_PROFILE_MUSIC_STANDARD"
                ),
                "videoOption" to mapOf(
                    "fps" to setting.fps,
                    "codec" to "H264",
                    "bitrate" to setting.bitrate,
                    "width" to setting.width,
                    "height" to setting.height,
                    "lowBitrateHighQuality" to false,
                ),
                "seiOption" to mapOf(
                    "source" to mapOf("agora" to mapOf("uidVideoLayout" to true)),
                    "sink" to mapOf("type" to 100),
                ),
            )
            outputs.add(outPut)
        }

        val cloudTranscoder = mapOf(
            "serviceType" to "cloudTranscoderV2",
            "config" to mapOf(
                "transcoder" to mapOf(
                    "idleTimeout" to 300,
                    "audioInputs" to audioInputs,
                    "videoInputs" to videoInputs,
                    "canvas" to mapOf(
                        "width" to setting.width,
                        "height" to setting.height,
                        "color" to 0,
                        "backgroundImage" to null,
                        "fillMode" to "FIT"
                    ),
//                    "waterMarks" to waterMarks,
                    "outputs" to outputs,
                ),
            ),
        )
        return mapOf("services" to mapOf("cloudTranscoder" to cloudTranscoder))
    }

    // 创建云端转码
    fun createCloudTranscoder(setting: TranscodeSetting, completion: (String?, Exception?) -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                LogTool.d(TAG, "createCloudTranscoder start transcodeUid:${setting.output.uid}")
                // uid 负数合图会失败
                taskId = reqCreateCloudTranscoder(setting)
                completion.invoke(taskId, null)
                LogTool.d(TAG, "createCloudTranscoder success taskId:$taskId, tokenName:$tokenName")
            } catch (ex: Exception) {
                completion.invoke(null, ex)
                LogTool.e(TAG, "createCloudTranscoder failure:${ex.message}")
            }
        }
    }

    /**
     * 查询云端播放器
     */
    fun queryCloudPlayer(completion: (ResponseCloudTranscoder?, Exception?) -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                val cloudTranscoder = reqQueryCloudTranscoder()
                completion.invoke(cloudTranscoder, null)
                LogTool.d(TAG, "queryCloudPlayer success:$cloudTranscoder")
            } catch (ex: Exception) {
                completion.invoke(null, ex)
                LogTool.e(TAG, "queryCloudPlayer failure ${ex.message}")
            }
        }
    }

    // 更新指定的云端转码
    fun updateCloudTranscoder(setting: TranscodeSetting, completion: (Boolean, Exception?) -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                LogTool.d(TAG, "updateCloudTranscoder transcodeUid:$setting")
                val success = reqUpdateCloudTranscoder(setting)
                completion.invoke(success, null)
                LogTool.d(TAG, "updateCloudTranscoder success taskId:$taskId, tokenName:$tokenName")
            } catch (ex: Exception) {
                completion.invoke(false, ex)
                LogTool.e(TAG, "updateCloudTranscoder failure:${ex.message}")
            }
        }
    }

    fun reqDeleteCloudTranscoder(completion: (Boolean, Exception?) -> Unit) {
        if (taskId.isEmpty()) {
            return
        }
        scope.launch(Dispatchers.Main) {
            try {
                if (tokenName.isEmpty()) {
                    completion.invoke(false, Exception("token name is null"))
                    return@launch
                }
                LogTool.d(TAG, "reqDeleteCloudTranscoder taskId:$taskId")
                val success = reqDeleteCloudTranscoder(taskId, tokenName)
                if (success) {
                    taskId = ""
                }
                completion.invoke(success, null)
                LogTool.d(TAG, "reqDeleteCloudTranscoder success $success")
            } catch (ex: Exception) {
                completion.invoke(false, ex)
                LogTool.e(TAG, "createCloudTranscoder failure:${ex.message}")
            }
        }
    }
}