package io.agora.mediarelay.rtc.transcoder

import android.util.Log
import com.google.gson.Gson
import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class RestfulTranscoder constructor(
    private val appId: String,
    customerKey: String,
    secret: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .addInterceptor(CurlInterceptor(logger = object : Logger {
            override fun log(message: String) {
                Log.d("curl", message)
            }
        }))
//        .addInterceptor()
        .build()

    private val instanceId = UUID.randomUUID().toString()

    private val gson = Gson()

    private var builderToken: String? = null

    private val host = "api.sd-rtn.com"

    private val apiVersion = "/v1/projects/"

    private val author = TranscodeAuthor(customerKey, secret)

    private var taskId: String? = null

    private var updateSequenceId: Int = 0

    private fun mayAcquire(completion: ((tokenName: String?) -> Unit)?) {
        if (!builderToken.isNullOrEmpty()) {
            completion?.invoke(builderToken)
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
                if (responseBody == null) {
                    completion?.invoke(null)
                    return
                }
                val json = JSONObject(responseBody)
                if (!json.has("tokenName")) {
                    completion?.invoke(null)
                    return
                }
                val tokenName = json.getString("tokenName")
                builderToken = tokenName
                completion?.invoke(tokenName)
            }

            override fun onFailure(call: Call, e: IOException) {
                completion?.invoke(null)
            }
        })
    }

    fun startRtmpStreamWithTranscoding(setting: TranscodeSetting, completion: ((succeed: Boolean) -> Unit)?) {
        // reset builderToken
        builderToken = null
        mayAcquire { tokenName ->
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
                        if (responseBody == null) {
                            completion?.invoke(false)
                            return
                        }
                        val json = JSONObject(responseBody)
                        if (!json.has("taskId")) {
                            completion?.invoke(false)
                            return
                        }
                        this@RestfulTranscoder.taskId = json.getString("taskId")
                        completion?.invoke(true)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        completion?.invoke(false)
                    }
                })
            } else {
                startRtmpStreamWithTranscoding(setting, completion)
            }
        }
    }

    private fun queryRtmpTranscoding(completion: ((succeed: Boolean) -> Unit)?) {
        val taskId = this.taskId ?: run {
            completion?.invoke(true)
            return
        }
        mayAcquire { tokenName ->
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
                        Log.d("aaa", "Response: $responseBody")
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.d("aaa", "Network request failed: ${e.message}")
                    }
                })
            } else {
                queryRtmpTranscoding(completion)
            }
        }
    }

    fun updateRtmpTranscoding(setting: TranscodeSetting, completion: ((succeed: Boolean) -> Unit)?) {
        val taskId = this.taskId ?: run {
            completion?.invoke(false)
            return
        }
        mayAcquire { tokenName ->
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
                        if (response.code == 200) {
                            completion?.invoke(true)
                        } else {
                            completion?.invoke(false)
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        completion?.invoke(false)
                    }
                })
            } else {
                updateRtmpTranscoding(setting, completion)
            }
        }
    }

    fun stopRtmpStream(completion: ((succeed: Boolean) -> Unit)?) {
        val taskId = this.taskId ?: run {
            completion?.invoke(true)
            return
        }
        mayAcquire { tokenName ->
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
                        if (response.code == 200) {
                            this@RestfulTranscoder.taskId = null
                            completion?.invoke(true)
                        } else {
                            completion?.invoke(false)
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        completion?.invoke(false)
                    }
                })
            } else {
                stopRtmpStream(completion)
            }
        }
    }

    private fun getURL(api: String): String {
        return "https://$host$apiVersion$appId$api"
    }

    private fun dataMapFromSetting(setting: TranscodeSetting): Map<String, Any> {
        val rtcToken = appId
        val audioInputs = mutableListOf<Map<String, *>>()
        val videoInputs = mutableListOf<Map<String, *>>()
        setting.inputItems.forEach { item ->
            val audioInput = mapOf(
                "rtc" to mapOf(
                    "rtcChannel" to setting.rtcChannel,
                    "rtcUid" to item.uid,
                    "rtcToken" to rtcToken
                )
            )
            val videoInput = mapOf(
                "rtc" to mapOf(
                    "rtcChannel" to setting.rtcChannel,
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
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}