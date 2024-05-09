package io.agora.mediarelay

import io.agora.media.RtcTokenBuilder
import io.agora.mediarelay.tools.LogTool
import java.util.*

enum class AudienceStatus {
    CDN_Audience,
    RTC_Audience,
    RTC_Broadcaster
}

data class AlphaGift constructor(
    val url: String,
    val mode: Int,
    val name: String,
)

/**
 * @author create by zhangwei03
 */
object KeyCenter {

    /**
     * 1、alpha礼物
     *  mPlayerView = new TextureView(getContext());
     *  mPlayerView.setOpaque(false);
     *  mLocalContainer.addView(mPlayerView);
     *
     *  mpk.setPlayerOption("alpha_stitch_mode", 4);
     *
     * 0：Do not split alpha channel(default)
     * 1：alpha is in the upper half
     * 2：alpha is in the lower half
     * 3：alpha is on the left
     * 4：alpha is at the right
     */


    private const val alphaGift1 = "http://114.236.93.153:8080/download/video/alpha_mp4/right/video_146_right.mp4"
    private const val alphaGift2 = "http://114.236.93.153:8080/download/video/alpha_mp4/right/video_151_right.mp4"
    private const val alphaGift3 = "http://114.236.93.153:8080/download/video/alpha_mp4/left/DG_left.mp4"
    private const val alphaGift4 = "http://114.236.93.153:8080/download/video/alpha_mp4/left/EAGLE_left.mp4"
    private const val alphaGift11 = "/assets/video_146_right.mp4"
    private const val alphaGift22 = "/assets/video_151_right.mp4"

    val alphaGiftList: Array<AlphaGift> by lazy {
        arrayOf(
//            AlphaGift(alphaGift1, 4, "gift1"),
//            AlphaGift(alphaGift2, 4, "gift2"),
//            AlphaGift(alphaGift3, 3, "gift3"),
//            AlphaGift(alphaGift4, 3, "gift4")
            AlphaGift(alphaGift11, 4, "gift146"),
            AlphaGift(alphaGift22, 4, "gift151")
        )
    }

//    private const val AGORA_PUSH_URL = "rtmp://examplepush.agoramdn.com/live/"
//    private const val AGORA_PULL_URL = "http://examplepull.agoramdn.com/live/"

    private const val AGORA_PUSH_URL = "rtmp://push.webdemo.agoraio.cn/live/"
    private const val AGORA_PULL_URL = "http://pull.webdemo.agoraio.cn/live/"

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

    private var innerRtcAudienceUid: String = ""

    /**用户 account*/
    fun rtcAccount(isBroadcast: Boolean, channelId: String): String {
        if (isBroadcast) return channelId
        if (innerRtcAudienceUid.isEmpty()) innerRtcAudienceUid = Math.abs(UUID.randomUUID().hashCode()).toString()
        return innerRtcAudienceUid
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