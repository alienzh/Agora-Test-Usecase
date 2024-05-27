package io.agora.mediarelay

import io.agora.media.RtcTokenBuilder
import io.agora.mediarelay.tools.LogTool
import java.util.*

enum class AudienceStatus {
    CDN_Audience,
    RTC_Audience,
    RTC_Broadcaster
}

enum class CdnMakes {
    Agora,
    Huawei,
    Tecent,
    Ali,
    Custom
}

data class CdnUrlModel constructor(
    val bitrate: String,
    val url: String
)

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

//    private const val AGORA_PUSH_URL = "rtmp://push.webdemo.agoraio.cn/live/"
//    private const val AGORA_PULL_URL = "http://pull.webdemo.agoraio.cn/live/"

    private const val AGORA_PUSH_URL = "rtmp://examplepush.agoramdn.com/live/"
    private const val AGORA_PULL_URL = "http://examplepull.agoramdn.com/live/"

    private const val push_huawei ="rtmp://push-hw.jacocdn.com/live/5036068430853962_main?hwSecret=756d6d31b288c173ee50beb4ac37528ec3dfe8a3fc3e15880340ea90228de807&hwTime=6657f41d"
    private const val push_tecent = "rtmp://tlive-push.jacocdn.com/tlive/5036068430853962_backup?txSecret=8981e94439a2e391f0a7b771acad8e23&txTime=6657F401"
    private const val push_ali ="rtmp://ali-push.jacocdn.com/live/5036068430853962_ali?auth_key=1717040063-0-0-2c002815a52e15ada1ec82b25db056bc"

    private const val pull_huawei_720p ="http://play-hw.jacocdn.com/live/5036068430853962_main_720P.flv?hwSecret=ed5487af6ade4ebfe5c0d410e983b2b4c85b4a641f204ba55b2a2dc19463d500&hwTime=66554e34"
    private const val pull_huawei_540p ="http://play-hw.jacocdn.com/live/5036068430853962_main_540P.flv?hwSecret=b6b8125b549dea5e9fe2ce6153794a19ea8a7592d74768dc086479de59c2120a&hwTime=66554e34"
    private const val pull_huawei_1080p ="http://play-hw.jacocdn.com/live/5036068430853962_main.flv?hwSecret=5ac82aa628c8252cb68d0bbc62a5e8beabb6c8c54412880511bdd766af4c8c9f&hwTime=66554e34"

    private const val pull_tecent_720p ="http://tlive-play.jacocdn.com/tlive/5036068430853962_backup_720P.flv?txSecret=b9d82281bc971b6c1b8a414649ad6079&txTime=66554E22"
    private const val pull_tecent_540p ="http://tlive-play.jacocdn.com/tlive/5036068430853962_backup_540P.flv?txSecret=970e67916a0c97feb8f9b96db66e2e86&txTime=66554E22"
    private const val pull_tecent_1080p ="http://tlive-play.jacocdn.com/tlive/5036068430853962_backup.flv?txSecret=511a35deaeca7af79f2583ea610fc7d6&txTime=66554E22"

    private const val pull_ali_720p ="https://ali-play.jacocdn.com/live/5036068430853962_ali_720P.flv?auth_key=1716866612-0-0-740fc244fd3119689036b3770afbdec6"
    private const val pull_ali_540p ="https://ali-play.jacocdn.com/live/5036068430853962_ali_540P.flv?auth_key=1716866612-0-0-b1d9905ae42a71ea8eb22e85ae6cdd42"
    private const val pull_ali_1080p ="https://ali-play.jacocdn.com/live/5036068430853962_ali.flv?auth_key=1716866612-0-0-cb9b715619f4ed1efd707e23263acb3c"

  // cdn 分辨率
    var mBitrateList = arrayOf<String?>("1080p", "720p")

    private const val replaceChannel = "channle"

    private const val urlPre = "agdemo"

    private val mAgoraCdnPullList = arrayOf(
        CdnUrlModel("1080p", "$AGORA_PULL_URL$urlPre${replaceChannel}_1080p.flv"),
        CdnUrlModel("720p", "$AGORA_PULL_URL$urlPre${replaceChannel}_720p.flv"),
    )

    private val mHuaweiCdnPullList = arrayOf(
        CdnUrlModel("1080p", pull_huawei_1080p),
        CdnUrlModel("720p", pull_huawei_720p),
        CdnUrlModel("540p", pull_huawei_540p)
    )

    private val mTecentCdnPullList = arrayOf(
        CdnUrlModel("1080p", pull_tecent_1080p),
        CdnUrlModel("720p", pull_tecent_720p),
        CdnUrlModel("540p", pull_tecent_540p)
    )

    private val mAliCdnPullList = arrayOf(
        CdnUrlModel("1080p", pull_ali_1080p),
        CdnUrlModel("720p", pull_ali_720p),
        CdnUrlModel("540p", pull_ali_540p)
    )

    var cdnMakes: CdnMakes = CdnMakes.Agora
        set(newValue) {
            field = newValue
            when (newValue) {
                CdnMakes.Huawei -> {
                    innerPushUrl = push_huawei
                    innerPullUrl = mHuaweiCdnPullList[0].url
                    mBitrateList = arrayOfNulls(mHuaweiCdnPullList.size)
                    for (i in mHuaweiCdnPullList.indices) {
                        mBitrateList[i] = mHuaweiCdnPullList[i].bitrate
                    }
                }

                CdnMakes.Tecent -> {
                    innerPushUrl = push_tecent
                    innerPullUrl = mTecentCdnPullList[0].url
                    mBitrateList = arrayOfNulls(mTecentCdnPullList.size)
                    for (i in mTecentCdnPullList.indices) {
                        mBitrateList[i] = mTecentCdnPullList[i].bitrate
                    }
                }

                CdnMakes.Ali -> {
                    innerPushUrl = push_ali
                    innerPullUrl = mAliCdnPullList[0].url
                    mBitrateList = arrayOfNulls(mAliCdnPullList.size)
                    for (i in mAliCdnPullList.indices) {
                        mBitrateList[i] = mAliCdnPullList[i].bitrate
                    }
                }
                CdnMakes.Custom -> {
                    innerPushUrl = ""
                    innerPullUrl = ""
                    mBitrateList = arrayOf("custom")
                }

                else -> {
                    innerPushUrl = AGORA_PUSH_URL
                    innerPullUrl = AGORA_PULL_URL
                    mBitrateList = arrayOf("1080p", "720p")
                    mBitrateList = arrayOfNulls(mAgoraCdnPullList.size)
                    for (i in mAgoraCdnPullList.indices) {
                        mBitrateList[i] = mAgoraCdnPullList[i].bitrate
                    }
                }
            }
        }

    val MAX_META_SIZE = 1024

    private var innerPushUrl = AGORA_PUSH_URL
    private var innerPullUrl = AGORA_PULL_URL

    fun setCustomPushUrl(pushUrl:String){
        this.innerPushUrl = pushUrl
    }

    fun setCustomPullUrl(pushUrl:String){
        this.innerPullUrl = pushUrl
    }

    val mPushUrl: String
        get() = innerPushUrl

    val mPullUrl: String
        get() = innerPullUrl


    private var innerRtcAudienceUid: String = ""

    /**用户 account*/
    fun rtcAccount(isBroadcast: Boolean, channelId: String): String {
        if (isBroadcast) return channelId
        if (innerRtcAudienceUid.isEmpty()) innerRtcAudienceUid = Math.abs(UUID.randomUUID().hashCode()).toString()
        return innerRtcAudienceUid
    }

    /**cdn push url*/
    fun getRtmpPushUrl(channelId: String): String {
        return if (cdnMakes == CdnMakes.Agora) {
            "$innerPushUrl$urlPre$channelId"
        } else {
            innerPushUrl
        }
    }

    /**cdn pull url*/
    fun getRtmpPullUrl(channelId: String, position: Int = 0): String {
        return when (cdnMakes) {
            CdnMakes.Huawei -> mHuaweiCdnPullList[position].url
            CdnMakes.Tecent -> mTecentCdnPullList[position].url
            CdnMakes.Ali -> mAliCdnPullList[position].url
            else -> mAgoraCdnPullList[position].url.replace(replaceChannel, channelId)
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