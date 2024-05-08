package io.agora.mediarelay.rtc.transcoder

import android.util.Log
import com.google.gson.Gson
import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import io.agora.logging.LogManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit


class RetryInterceptor constructor(
    private var maxRetryCount: Int,
    private var retryDelayMillis: Int
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var retryCount = 0
        do {
            try {
                response = chain.proceed(request)
            } catch (e: IOException) {
                // 请求失败，重试
                if (retryCount < maxRetryCount) {
                    retryCount++
                    try {
                        TimeUnit.MILLISECONDS.sleep(retryDelayMillis.toLong())
                    } catch (sleepException: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return response!!
                    }
                } else {
                    throw e
                }
            }
        } while (response == null)
        return response
    }
}


class RestfulTranscoder constructor(
    private val appId: String,
    customerKey: String,
    secret: String
) {

    private val logger by lazy {
        LogManager.instance()
    }

    private val TAG = "Restful_TAG"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .addInterceptor(CurlInterceptor(logger = object : Logger {
            override fun log(message: String) {
                logger.info("curl", message)
            }
        }))
        .retryOnConnectionFailure(false)
        .addInterceptor(RetryInterceptor(3, 3000))
        .build()

    private val instanceId = UUID.randomUUID().toString()

    private val gson = Gson()

    private var builderToken: String? = null

    //    private val host = "https://api.sd-rtn.com"
//    private val host = "http://112.13.168.202:16000"
    private val host = "http://183.131.160.228:16000"

    private val apiVersion = "/v1/projects/"

    private val author = TranscodeAuthor(customerKey, secret)

    private var taskId: String? = null

    private var updateSequenceId: Int = 0

    private fun mayAcquire(completion: ((tokenName: String?, code: Int, message: String) -> Unit)?) {
        if (!builderToken.isNullOrEmpty()) {
            logger.info(TAG, "already acquire token：$builderToken")
            completion?.invoke(builderToken, 200, "already acquire token")
            return
        }
        val api = "/rtsc/cloud-transcoder/builderTokens"
        val map = mapOf("instanceId" to instanceId)
        val jsonString = gson.toJson(map)
        val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(getURL(api))
            .header("Authorization", author.basicAuth())
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                logger.info(TAG, "acquire, code:${response.code}, $responseBody")
                if (responseBody == null) {
                    completion?.invoke(null, response.code, response.message)
                    return
                }
                val json = JSONObject(responseBody)
                if (!json.has("tokenName")) {
                    completion?.invoke(null, response.code, responseBody)
                    return
                }
                val tokenName = json.getString("tokenName")
                builderToken = tokenName
                completion?.invoke(tokenName, response.code, responseBody)
            }

            override fun onFailure(call: Call, e: IOException) {
                logger.error(TAG, "acquire ：$e")
                completion?.invoke(null, -100, "http onFailure")
            }
        })
    }

    fun startRtmpStreamWithTranscoding(
        setting: TranscodeSetting,
        completion: ((succeed: Boolean, code: Int, message: String) -> Unit)?
    ) {
        // reset builderToken
        builderToken = null
        mayAcquire { tokenName, code, message ->
            if (tokenName != null) {
                val api = "/rtsc/cloud-transcoder/tasks?builderToken=$tokenName"
                val map = dataMapFromSetting(setting)
                val jsonString = gson.toJson(map)
                val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(getURL(api))
                    .header("Authorization", author.basicAuth())
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        logger.info(TAG, "create, code:${response.code}, $responseBody")
                        if (responseBody == null) {
                            completion?.invoke(false, response.code, response.message)
                            return
                        }
                        if (response.code != 200) {
                            if (response.code == 206) {
                                queryRtmpTranscoding { }
                            }
                            completion?.invoke(false, response.code, response.message)
                            return
                        }
                        val json = JSONObject(responseBody)
                        if (!json.has("taskId")) {
                            completion?.invoke(false, response.code, responseBody)
                            return
                        }
                        this@RestfulTranscoder.taskId = json.getString("taskId")
                        completion?.invoke(true, response.code, responseBody)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        logger.error(TAG, "create ：$e")
                        completion?.invoke(false, -100, e.message ?: "http onFailure")
                    }
                })
            }
        }
    }

    private fun queryRtmpTranscoding(completion: ((succeed: Boolean) -> Unit)?) {
        val taskId = this.taskId ?: run {
            logger.info(TAG, "query but taskId empty")
            completion?.invoke(true)
            return
        }
        mayAcquire { tokenName, code, message ->
            if (tokenName != null) {
                val api = "/rtsc/cloud-transcoder/tasks/$taskId"
                val builder = getURL(api).toHttpUrl().newBuilder()
                builder.addQueryParameter("builderToken", tokenName)
                val url = builder.build()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", author.basicAuth())
                    .header("Content-Type", "application/json")
                    .get()
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        logger.info(TAG, "query, code:${response.code}, $responseBody")
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        logger.error(TAG, "query：$e")
                    }
                })
            }
        }
    }

    fun updateRtmpTranscoding(
        setting: TranscodeSetting, completion: ((succeed: Boolean, code: Int, message: String) -> Unit)?
    ) {
        val taskId = this.taskId ?: run {
            logger.info(TAG, "update but taskId empty")
            completion?.invoke(false, -100, "update rtmp but taskId is empty")
            return
        }
        mayAcquire { tokenName, code, message ->
            if (tokenName != null) {
                updateSequenceId++
                val api = "/rtsc/cloud-transcoder/tasks/$taskId"
                val builder = getURL(api).toHttpUrl().newBuilder()
                builder.addQueryParameter("builderToken", tokenName)
                builder.addQueryParameter("sequenceId", updateSequenceId.toString())
                builder.addQueryParameter("updateMask", "services.cloudTranscoder.config")
                val url = builder.build()
                val map = dataMapFromSetting(setting)
                val jsonString = gson.toJson(map)
                val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", author.basicAuth())
                    .header("Content-Type", "application/json")
                    .patch(requestBody)
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        logger.info(TAG, "update, code:${response.code}, $responseBody")
                        if (response.code == 200) {
                            completion?.invoke(true, response.code, responseBody ?: "")
                        } else {
                            completion?.invoke(false, response.code, responseBody ?: response.message)
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        logger.error(TAG, "update：$e")
                        completion?.invoke(false, -100, e.message ?: "http onFailure")
                    }
                })
            }
        }
    }

    fun stopRtmpStream(completion: ((succeed: Boolean, code: Int, message: String) -> Unit)?) {
        val taskId = this.taskId ?: run {
            logger.info(TAG, "stop but taskId empty")
            completion?.invoke(true, -100, "stop rtmp but taskId is empty")
            return
        }
        mayAcquire { tokenName, code, message ->
            if (tokenName != null) {
                val api = "/rtsc/cloud-transcoder/tasks/$taskId?builderToken=$tokenName"
                val request = Request.Builder()
                    .url(getURL(api))
                    .header("Authorization", author.basicAuth())
                    .header("Content-Type", "application/json")
                    .delete()
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        logger.info(TAG, "delete, code:${response.code}, $responseBody")
                        if (response.code == 200) {
                            this@RestfulTranscoder.taskId = null
                            completion?.invoke(true, response.code, responseBody ?: "")
                        } else {
                            completion?.invoke(false, response.code, responseBody ?: response.message)
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        logger.error(TAG, "delete：$e")
                        completion?.invoke(false, -100, e.message ?: "http onFailure")
                    }
                })
            }
        }
    }

    private fun getURL(api: String): String {
        return "$host$apiVersion$appId$api"
    }

    private fun dataMapFromSetting(setting: TranscodeSetting): Map<String, Any> {
        val rtcToken = appId
        val audioInputs = mutableListOf<Map<String, *>>()
        val videoInputs = mutableListOf<Map<String, *>>()
        setting.inputItems.forEach { item ->
            val audioInput = mapOf(
                "rtc" to mapOf(
                    "rtcChannel" to item.channel,
                    "rtcUid" to item.uid,
                    "rtcToken" to rtcToken
                ),
            )
            val videoInput = mapOf(
                "rtc" to mapOf(
                    "rtcChannel" to item.channel,
                    "rtcUid" to item.uid,
                    "rtcToken" to rtcToken,
                ),
                "placeholderImageUrl" to null,
                "region" to mapOf(
                    "x" to item.x,
                    "y" to item.y,
                    "width" to item.width,
                    "height" to item.height,
                    "zOrder" to 1,
                )
            )
            audioInputs.add(audioInput)
            videoInputs.add(videoInput)
        }
        return mapOf(
            "services" to mapOf(
                "cloudTranscoder" to mapOf(
                    "serviceType" to "cloudTranscoderV2",
                    "config" to mapOf(
                        "transcoder" to mapOf(
                            "idleTimeout" to 300,
                            "audioInputs" to audioInputs,
                            "canvas" to mapOf(
                                "width" to setting.width,
                                "height" to setting.height,
                                "color" to 0,
                                "backgroundImage" to null,
                                "fillMode" to "FIT"
                            ),
                            "waterMarks" to null,
                            "videoInputs" to videoInputs,
                            "outputs" to listOf(
                                mapOf(
                                    "streamUrl" to setting.cdnURL,
                                    "audioOption" to mapOf(
                                        "profileType" to "AUDIO_PROFILE_MUSIC_STANDARD"
                                    ),
                                    "videoOption" to mapOf(
                                        "fps" to setting.fps,
                                        "codec" to "H265",
                                        "bitrate" to setting.bitrate,
                                        "width" to setting.width,
                                        "height" to setting.height,
                                        "lowBitrateHighQuality" to false,
                                    ),
                                    "seiOption" to mapOf(
                                        "source" to emptyMap<String, Any>(),
                                        "sink" to mapOf(
                                            "type" to 100,
                                            "aliyun" to true,
                                            "info" to emptyMap<String, Any>()
                                        )
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}