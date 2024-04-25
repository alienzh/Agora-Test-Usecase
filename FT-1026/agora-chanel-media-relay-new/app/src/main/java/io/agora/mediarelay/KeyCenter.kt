package io.agora.mediarelay

import io.agora.media.RtcTokenBuilder
import io.agora.mediarelay.tools.LogTool
import java.util.*

/**
 * @author create by zhangwei03
 */
object KeyCenter {

//    private const val AGORA_PUSH_URL = "rtmp://examplepush.agoramdn.com/live/"
//    private const val AGORA_PULL_URL = "http://examplepull.agoramdn.com/live/"

    private const val AGORA_PUSH_URL = "rtmp://push.webdemo.agoraio.cn/live/"
    private const val AGORA_PULL_URL = "https://pull.webdemo.agoraio.cn/live/"

    private const val CUSTOM_PUSH_URL =
        "rtmp://193.122.93.11:1935/2058423337/4996731320601635_obs?zgToken=db71ff614d41372383390c8b0e4f8d27d042c9e4d852dec21b9e03ee92d84c66&zgExpired=1706969844&zgNonce=1706797044862&zgVer=v1"
    private const val CUSTOM_PULL_URL = ""

    // cdn 分辨率
    val mBitrateList = arrayOf("720p", "1080p")

    private const val urlPre = "agdemo"

    val MAX_META_SIZE = 1024

    var pushUrl = AGORA_PUSH_URL
    var pullUrl = AGORA_PULL_URL

    fun setupAgoraCdn(agora: Boolean) {
        if (agora) {
            pushUrl = AGORA_PUSH_URL
            pullUrl = AGORA_PULL_URL
        } else {
            pushUrl = CUSTOM_PUSH_URL
            pullUrl = CUSTOM_PULL_URL
        }
    }

    fun isAgoraCdn(): Boolean {
        return pushUrl == AGORA_PUSH_URL && pullUrl == AGORA_PULL_URL
    }

    var rtcAudienceUid: Int = 0

    /**房主uid 为房间id*/
    fun rtcUid(isBroadcast: Boolean, channelId: String): Int {
        if (isBroadcast) return channelId.toIntOrNull() ?: 123
        if (rtcAudienceUid == 0) rtcAudienceUid = UUID.randomUUID().hashCode()
        return Math.abs(rtcAudienceUid)
    }

    /**cdn push url*/
    fun getRtmpPushUrl(channelId: String): String {
        return if (pushUrl == AGORA_PUSH_URL) {
            "$pushUrl$urlPre$channelId"
        } else {
            pushUrl
        }
    }

    /**cdn pull url*/
    fun getRtmpPullUrl(channelId: String, position: Int = 0): String {
        return if (pullUrl == AGORA_PULL_URL) {
            if (position > 0 && position < mBitrateList.size) {
                "$pullUrl$urlPre${channelId}_las${mBitrateList[position]}.flv"
            } else {
                "$pullUrl$urlPre$channelId.flv"
            }
        } else {
            pullUrl
        }
    }

    fun getRtcToken(channelId: String, uid: Int): String {
        var rtcToken: String = ""
        try {
            rtcToken = RtcTokenBuilder().buildTokenWithUid(
                BuildConfig.AGORA_APP_ID, BuildConfig.AGORA_CUSTOMER_SECRET, channelId, uid,
                RtcTokenBuilder.Role.Role_Publisher, 0
            )
        } catch (e: Exception) {
            LogTool.e("rtc token build error:${e.message}")
        }
        return rtcToken
    }
}