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

    private const val push_huawei =
        "rtmp://ali-push.jacocdn.com/live/5040102000756628_ali?auth_key=1718001797-0-0-24dad007faa01ec5d9ee48fb91139380"
    private const val push_tecent =
        "rtmp://tlive-push.jacocdn.com/tlive/5040102000756628_backup?txSecret=1bbaaf7491823f319cda6e5267f67f10&txTime=6666A0A3"
    private const val push_ali =
        "rtmp://push-hw.jacocdn.com/live/5040102000756628_main?hwSecret=376e219a3eb75d51b99b95a29705a329270aa98994576e7fde250f61295b43ec&hwTime=6666a0bb"


    private const val pull_huawei_720p =
        "http://play-hw.jacocdn.com/live/5040102000756628_main_720P.flv?hwSecret=5f73bbcaf94f5be15e4653bb0b7297cdf0a18affa70fd7c1ddd8a1e7941457a0&hwTime=665ac35c"
    private const val pull_huawei_540p =
        "http://play-hw.jacocdn.com/live/5040102000756628_main_540P.flv?hwSecret=972a690be05899f720bfc9ab589ce9eb0c005a45c18bda8cd4e2f2bbf38c84a8&hwTime=665ac35c"
    private const val pull_huawei_1080p =
        "http://play-hw.jacocdn.com/live/5040102000756628_main.flv?hwSecret=b5abe6603ed4d73739cf772cdf5704eeb1f3eda215346b823cff570feef17b00&hwTime=665ac35c"

    private const val pull_tecent_720p =
        "http://tlive-play.jacocdn.com/tlive/5040102000756628_backup_720P.flv?txSecret=c3718131a85793dc0fd6fc44d64ff7ae&txTime=665AC385"
    private const val pull_tecent_540p =
        "http://tlive-play.jacocdn.com/tlive/5040102000756628_backup_540P.flv?txSecret=0de7a68ae85e186dfa1f4c4dedd4b7fc&txTime=665AC385"
    private const val pull_tecent_1080p =
        "http://tlive-play.jacocdn.com/tlive/5040102000756628_backup.flv?txSecret=b4cc5ff8251ec52ad455d5530106853e&txTime=665AC385"

    private const val pull_ali_720p =
        "http://ali-play.jacocdn.com/live/5040102000756628_ali_720P.flv?auth_key=1717224284-0-0-9389aabc9abe7a3bdc133912eff272a1"
    private const val pull_ali_540p =
        "http://ali-play.jacocdn.com/live/5040102000756628_ali_540P.flv?auth_key=1717224284-0-0-a87e28bcf60eb742e539ffb25c0850dc"
    private const val pull_ali_1080p =
        "http://ali-play.jacocdn.com/live/5040102000756628_ali.flv?auth_key=1717224284-0-0-3f4b8da188bbdad403877af5882efa41"

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

    fun setCustomPushUrl(pushUrl: String) {
        this.innerPushUrl = pushUrl
    }

    fun setCustomPullUrl(pushUrl: String) {
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
}