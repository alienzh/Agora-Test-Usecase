package io.agora.mediarelay.rtc.transcoder

import android.util.Log
import com.google.gson.Gson
import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import io.agora.logging.LogManager
import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.KeyCenter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// 跨厂商转推, 只支持 string uid
class AgoraZegoRestfulTranscoder constructor(
    private val appId: String = BuildConfig.AGORA_APP_ID,
    private val accessKey: String = BuildConfig.AGORA_ACCESS_KEY,
    private val secretKey: String = BuildConfig.AGORA_SECRET_KEY,

    ) {

    private val logger by lazy {
        LogManager.instance()
    }

    private val TAG = "Agor_Zego_Restful_TAG"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .addInterceptor(CurlInterceptor(logger = object : Logger {
            override fun log(message: String) {
                logger.info("curl", message)
                Log.d("curl", message)
            }
        }))
        .retryOnConnectionFailure(false)
        .addInterceptor(RetryInterceptor(3, 3000))
        .build()

//    private val instanceId = UUID.randomUUID().toString()

    private val gson = Gson()

    private var builderToken: String? = null

    private val host = "http://101.64.234.51:16000"

    private val apiVersion = "/v1/projects/"

    private var taskId: String? = null

    private val author = TranscodeAuthor(accessKey, secretKey)

    private fun mayAcquire(uid: String, completion: ((tokenName: String?, code: Int, message: String) -> Unit)?) {
        if (!builderToken.isNullOrEmpty()) {
            logger.info(TAG, "already acquire token：$builderToken")
            completion?.invoke(builderToken, 200, "already acquire token")
            return
        }
        val api = "/rtsc/stream-converter/builderTokens"
        val testHost = KeyCenter.testIp.split(":")[0]
        val testPort = KeyCenter.testIp.split(":")[1]
        val map = mapOf(
            "taskLabels" to mapOf(
                "cname" to "rd-jaco",
                "uid" to uid,
            ),
            "testIp" to testHost,
            "testPort" to testPort,
            "testVersion" to "v3.12.5-s",
        )
        val jsonString = gson.toJson(map)
        val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(getURL(api))
//            .header("Authorization", author.basicAuth())
            .header("Authorization", accessKey)
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

    // agora 转推 zego
    fun startAgoraStreamWithTranscoding(
        setting: AgoraTranscodeSetting,
        completion: ((succeed: Boolean, code: Int, message: String) -> Unit)?
    ) {
        // reset builderToken
        builderToken = null
        mayAcquire(setting.rtcStringUid) { tokenName, code, message ->
            if (tokenName != null) {
                val api = "/rtsc/stream-converter/tasks?builderToken=$tokenName"
                val map = agoraDataMapFromSetting(setting)
                val jsonString = gson.toJson(map)
                val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(getURL(api))
//                    .header("Authorization", author.basicAuth())
                    .header("Authorization", accessKey)
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
//                            if (response.code == 206) {
//                                queryRtmpTranscoding(setting.rtcStringUid) { }
//                            }
                            completion?.invoke(false, response.code, response.message)
                            return
                        }
                        val json = JSONObject(responseBody)
                        if (!json.has("taskId")) {
                            completion?.invoke(false, response.code, responseBody)
                            return
                        }
                        this@AgoraZegoRestfulTranscoder.taskId = json.getString("taskId")
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

    // zego 转推 agora
    fun startZegoStreamWithTranscoding(
        setting: ZegoTranscodeSetting,
        completion: ((succeed: Boolean, code: Int, message: String) -> Unit)?
    ) {
        // reset builderToken
        builderToken = null
        mayAcquire(setting.rtcStringUid) { tokenName, code, message ->
            if (tokenName != null) {
                val api = "/rtsc/stream-converter/tasks?builderToken=$tokenName"
                val map = zegoDataMapFromSetting(setting)
                val jsonString = gson.toJson(map)
                val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(getURL(api))
//                    .header("Authorization", author.basicAuth())
                    .header("Authorization", accessKey)
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
//                            if (response.code == 206) {
//                                queryRtmpTranscoding(setting.rtcStringUid) { }
//                            }
                            completion?.invoke(false, response.code, response.message)
                            return
                        }
                        val json = JSONObject(responseBody)
                        if (!json.has("taskId")) {
                            completion?.invoke(false, response.code, responseBody)
                            return
                        }
                        this@AgoraZegoRestfulTranscoder.taskId = json.getString("taskId")
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

    private fun queryRtmpTranscoding(uid: String, completion: ((succeed: Boolean) -> Unit)?) {
//        val taskId = this.taskId ?: run {
//            logger.info(TAG, "query but taskId empty")
//            completion?.invoke(true)
//            return
//        }

        val token = this.builderToken ?: run {
            logger.info(TAG, "query but token empty")
            completion?.invoke(false)
            return
        }
        val api = "/rtsc/stream-converter/tasks?builderToken=$token"
        val builder = getURL(api).toHttpUrl().newBuilder()
        builder.addQueryParameter("builderToken", token)
        val url = builder.build()
        val request = Request.Builder()
            .url(url)
//                    .header("Authorization", author.basicAuth())
            .header("Authorization", accessKey)
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

    fun stopRtmpStream(stringUid: String, completion: ((succeed: Boolean, code: Int, message: String) -> Unit)?) {
//        val taskId = this.taskId ?: run {
//            logger.info(TAG, "stop but taskId empty")
//            completion?.invoke(true, -100, "stop rtmp but taskId is empty")
//            return
//        }

        val token = this.builderToken ?: run {
            logger.info(TAG, "stop but token empty")
            completion?.invoke(false, -100, "stop rtmp but token is empty")
            return
        }
        val api = "/rtsc/stream-converter/tasks/$taskId?builderToken=$token"
        val request = Request.Builder()
            .url(getURL(api))
//                    .header("Authorization", author.basicAuth())
            .header("Authorization", accessKey)
            .header("Content-Type", "application/json")
            .delete()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                logger.info(TAG, "delete, code:${response.code}, $responseBody")
                if (response.code == 200) {
                    this@AgoraZegoRestfulTranscoder.taskId = null
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

    private fun getURL(api: String): String {
        return "$host$apiVersion$appId$api"
    }

    private fun agoraDataMapFromSetting(setting: AgoraTranscodeSetting): Map<String, Any> {
        val rtcToken = appId
        val audioInputs = mutableListOf<Map<String, *>>()
        val videoInputs = mutableListOf<Map<String, *>>()
        setting.inputItems.forEach { item ->
            val audioRtcMap = mutableMapOf<String, Any>(
                "rtcChannel" to item.rtcChannel,
                "rtcToken" to rtcToken,
            )
            if (setting.enableUserAccount) {
                audioRtcMap["rtcStringUid"] = item.rtcAccount
                audioRtcMap["userAccount"] = "bot${item.rtcAccount}" //转推机器人使用的StringUid
            } else {
                // int uid 服务会自己生成 机器人 uid
                audioRtcMap["rtcUid"] = item.rtcUid
            }
            audioInputs.add(mapOf("rtc" to audioRtcMap))

            val videoRtcMap = mutableMapOf<String, Any>(
                "rtcChannel" to item.rtcChannel,
                "rtcToken" to rtcToken,
            )
            if (setting.enableUserAccount) {
                videoRtcMap["rtcStringUid"] = item.rtcAccount
                videoRtcMap["userAccount"] = "bot${item.rtcAccount}" //转推机器人使用的StringUid
            } else {
                // int uid 服务会自己生成 机器人 uid
                videoRtcMap["rtcUid"] = item.rtcUid
            }
            videoInputs.add(mapOf("rtc" to videoRtcMap))
        }

        val outputs = mutableListOf<Map<String, *>>()
        setting.outputItems.forEach { item ->
            val zegoMap = mutableMapOf<String, Any>(
                "appId" to item.zegoAppId,
                "appSign" to item.zegoAppSign,
                "token" to "",
                "roomId" to item.zegoRoomId,
                "robotUserId" to item.robotUserId,
                "robotUserName" to item.robotUserName,
                "streamId" to item.streamId,
            )
            outputs.add(mapOf("zegoRtc" to zegoMap))
        }

        val bodyMap = mutableMapOf(
            "subscribeConfig" to mapOf(
                "idleTimeout" to 100,
                "streamProcessMode" to "x-vendor-relay",
                "audioInputs" to audioInputs,
                "videoInputs" to videoInputs,
                "outputs" to outputs
            ),
        )
        return bodyMap
    }

    private fun zegoDataMapFromSetting(setting: ZegoTranscodeSetting): Map<String, Any> {
        val rtcToken = appId
        val audioInputs = mutableListOf<Map<String, *>>()
        val videoInputs = mutableListOf<Map<String, *>>()
        setting.inputItems.forEach { item ->
            val audioRtcMap = mutableMapOf<String, Any>(
                "appId" to item.zegoAppId,
                "appSign" to item.zegoAppSign,
                "token" to "",
                "roomId" to item.zegoRoomId,
                "robotUserId" to item.robotUserId,
                "robotUserName" to item.robotUserName,
                "streamId" to item.streamId,
            )
            val audioInput = mapOf("zegoRtc" to audioRtcMap)

            val videoRtcMap = mutableMapOf<String, Any>(
                "appId" to item.zegoAppId,
                "appSign" to item.zegoAppSign,
                "token" to "",
                "roomId" to item.zegoRoomId,
                "robotUserId" to item.robotUserId,
                "robotUserName" to item.robotUserName,
                "streamId" to item.streamId,
            )
            val videoInput = mapOf("zegoRtc" to videoRtcMap)
            audioInputs.add(audioInput)
            videoInputs.add(videoInput)
        }

        val outputs = mutableListOf<Map<String, *>>()
        setting.outputItems.forEach { item ->
            val rtcMap = mutableMapOf<String, Any>(
                "rtcToken" to rtcToken,
                "rtcChannel" to item.rtcChannel,
                "rtcStringUid" to "",
                "userAccount" to item.rtcStringUid,
            )
            outputs.add(mapOf("rtc" to rtcMap))
        }
        val bodyMap = mutableMapOf(
            "subscribeConfig" to mapOf(
                "idleTimeout" to 100,
                "streamProcessMode" to "x-vendor-relay",
                "audioInputs" to audioInputs,
                "videoInputs" to videoInputs,
                "outputs" to outputs
            ),
        )
        return bodyMap
    }
}