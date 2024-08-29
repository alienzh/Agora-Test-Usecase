package io.agora.mediarelay

import java.util.*

enum class Vendor {
    Agora,
    Zego,
}

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
        "rtmp://push-hw.jacocdn.com/live/5044124401841736_main?hwSecret=c240d103857ee956bdcb4e64e9958d0f12c6868bbf2d47e76f7589ab05beae94&hwTime=667541d3"
    private const val push_tecent =
        "rtmp://tlive-push.jacocdn.com/tlive/5044124401841736_backup?txSecret=d877138ed1724b348c9fe9f5db547079&txTime=667541F6"
    private const val push_ali =
        "rtmp://ali-push.jacocdn.com/live/5044124401841736_ali?auth_key=1718960650-0-0-316b6469557c1f8dad4b24bdd8176771"


    private const val pull_huawei_720p =
        "http://play-hw.jacocdn.com/live/5044124401841736_main_720P.flv?hwSecret=baa7af47348ecb13c6b53e8796c1b3699a624c3c755b1c0cecf409012f713be6&hwTime=666964ab"
    private const val pull_huawei_540p =
        "http://play-hw.jacocdn.com/live/5044124401841736_main_540P.flv?hwSecret=850f47df51d68aaaa1d9ec95ffe1683c0a0d794971c47a8f4e5fec058f070bbd&hwTime=666964ab"
    private const val pull_huawei_1080p =
        "http://play-hw.jacocdn.com/live/5044124401841736_main.flv?hwSecret=317fea88045b73cb0a566733926a04d75eb01093cbfafcbbdbfae04ac73538d4&hwTime=666964ab"

    private const val pull_tecent_720p =
        "http://tlive-play.jacocdn.com/tlive/5044124401841736_backup_720P.flv?txSecret=00156e5ded47b7bb38f0d85565857286&txTime=666964AB"
    private const val pull_tecent_540p =
        "http://tlive-play.jacocdn.com/tlive/5044124401841736_backup_540P.flv?txSecret=662c6b70b1790359031aa8b20fc3468e&txTime=666964AB"
    private const val pull_tecent_1080p =
        "http://tlive-play.jacocdn.com/tlive/5044124401841736_backup.flv?txSecret=a7d96a522a901304d138a30721b2b61e&txTime=666964AB"

    private const val pull_ali_720p =
        "http://ali-play.jacocdn.com/live/5044124401841736_ali_720P.flv?auth_key=1718183083-0-0-9a80b340d91c4e52492be732875f9dfb"
    private const val pull_ali_540p =
        "http://ali-play.jacocdn.com/live/5044124401841736_ali_540P.flv?auth_key=1718183083-0-0-25dcb930b9d354361a7ab25415771dc8"
    private const val pull_ali_1080p =
        "http://ali-play.jacocdn.com/live/5044124401841736_ali.flv?auth_key=1718183083-0-0-dfd7e708cca7c7eea522c1c7b86f7765"

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

    var vendor: Vendor = Vendor.Agora

    var testIp: String = "60.191.137.21:16667"
}