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

    private const val push_huawei ="rtmp://push-hw.jacocdn.com/live/5039394191805515_main?hwSecret=80cd8dbe9f98386569099240af1ef81bc0029dab33fda14db375917845168f58&hwTime=66640ce2"
    private const val push_tecent = "rtmp://tlive-push.jacocdn.com/tlive/5039394191805515_backup?txSecret=fa95913d8a7adada15ebe877517253c1&txTime=66640D01"
    private const val push_ali ="rtmp://ali-push.jacocdn.com/live/5039394191805515_ali?auth_key=1717832981-0-0-5f452bfd0363ab1044df153483464cb3"

    private const val pull_huawei_720p ="http://play-hw.jacocdn.com/live/5039394191805515_main_720P.flv?hwSecret=6b382e78c76a881a4da1949a92d6e796d98e16b92d5c8d290db9ebd87eb50bdd&hwTime=66582eae"
    private const val pull_huawei_540p ="http://play-hw.jacocdn.com/live/5039394191805515_main_540P.flv?hwSecret=a1d0433a0aa39b6d5564243731060e7c1086d4715ffc7d747d6e9266d1be7929&hwTime=66582eae"
    private const val pull_huawei_1080p ="http://play-hw.jacocdn.com/live/5039394191805515_main.flv?hwSecret=f46875aad359df2f48704d600c5656964e439b7125fb1cacb49ec7c3e5d2dab2&hwTime=66582eae"

    private const val pull_tecent_720p ="http://tlive-play.jacocdn.com/tlive/5039394191805515_backup_720P.flv?txSecret=b7fa70d5815b234476299a7f8035aa11&txTime=66582FDA"
    private const val pull_tecent_540p ="http://tlive-play.jacocdn.com/tlive/5039394191805515_backup_540P.flv?txSecret=de34885b5dcceac0f61632ddde87f8be&txTime=66582FDA"
    private const val pull_tecent_1080p ="http://tlive-play.jacocdn.com/tlive/5039394191805515_backup.flv?txSecret=d41f105725ea25a41599cc403fe83cdc&txTime=66582FDA"

    private const val pull_ali_720p ="https://ali-play.jacocdn.com/live/5039394191805515_ali_720P.flv?auth_key=1717055150-0-0-f11d0d48208c41bc7b8a7b23d4d179ee"
    private const val pull_ali_540p ="https://ali-play.jacocdn.com/live/5039394191805515_ali_540P.flv?auth_key=1717055150-0-0-39da507e920340c2c78fa9f5f3439028"
    private const val pull_ali_1080p ="https://ali-play.jacocdn.com/live/5039394191805515_ali.flv?auth_key=1717055150-0-0-12ae9c1b8b9fa0324ec715e69e15d26d"

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
}