package io.agora.mediarelay

import io.agora.media.RtcTokenBuilder
import io.agora.mediarelay.tools.LogTool
import java.util.*

/**
 * @author create by zhangwei03
 */
object KeyCenter {
    private const val AGORA_PUSH_URL = "rtmp://examplepush.agoramdn.com/live/"
    private const val AGORA_PULL_URL = "http://examplepull.agoramdn.com/live/"

    var rtcAudienceUid : Int = 0

    /**房主uid 为房间id*/
    fun rtcUid(isBroadcast: Boolean, channelId: String): Int {
        if (isBroadcast) return channelId.toIntOrNull() ?: 123
        if (rtcAudienceUid==0) rtcAudienceUid = UUID.randomUUID().hashCode()
        return rtcAudienceUid
    }

    /**cdn push url*/
    fun getRtmpPushUrl(channelId: String): String = "$AGORA_PUSH_URL$channelId"

    /**cdn pull url*/
    fun getRtmpPullUrl(channelId: String): String = "$AGORA_PULL_URL$channelId.flv"

    fun getRtcToken(channelId: String,uid:Int): String {
        var rtcToken: String = ""
        try {
            rtcToken = RtcTokenBuilder().buildTokenWithUid(
                BuildConfig.RTC_APP_ID, BuildConfig.RTC_APP_CERT, channelId, uid,
                RtcTokenBuilder.Role.Role_Publisher, 0
            )
        } catch (e: Exception) {
            LogTool.e("rtc token build error:${e.message}")
        }
        return rtcToken
    }
}