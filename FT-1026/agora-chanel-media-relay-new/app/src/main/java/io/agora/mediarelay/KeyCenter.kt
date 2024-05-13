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

    private const val AGORA_PUSH_URL = "rtmp://push.webdemo.agoraio.cn/live/"
    private const val AGORA_PULL_URL = "http://pull.webdemo.agoraio.cn/live/"

    private const val push_huawei ="rtmp://push-hw.jacocdn.com/live/5032563931251409_main?hwSecret=7e3ab2aed182b85f7be5e4f08a7030180d70b92449c0eedae3afca14b67183aa&hwTime=664b34e7"
    private const val push_tecent = "rtmp://tlive-push.jacocdn.com/tlive/5032563931251409_backup?txSecret=aaf16e113f9303d55e16f8e1c615f315&txTime=664B34D5"
    private const val push_ali ="rtmp://ali-push.jacocdn.com/live/5032563931251409_ali?auth_key=1716204714-0-0-6b010cea8f528ab172712529651e78b8"

    private const val pull_huawei_720p ="http://play-hw.jacocdn.com/live/5032563931251409_main_720P.flv?hwSecret=aaa5acf1ac5058bdbce3f889ae6d7cb8dc90e05aaba1ec8a106d4eb8e6a11905&hwTime=663f57a5"
    private const val pull_huawei_540p ="http://play-hw.jacocdn.com/live/5032563931251409_main_540P.flv?hwSecret=e3b9e35cb2b37c795bcbd6b2b7c26c5bda0e21367dc8c3cc3dc572dc219594f3&hwTime=663f57a5"
    private const val pull_huawei_1080p ="http://play-hw.jacocdn.com/live/5032563931251409_main.flv?hwSecret=a56a777233b1e21c808f62453e0e7e26d480cc1d8fd52059cdd993689a0023df&hwTime=663f57a5"

    private const val pull_tecent_720p ="http://tlive-play.jacocdn.com/tlive/5032563931251409_backup_720P.flv?txSecret=e2eebc01d8b4642062e0c7a79d5f29b7&txTime=663F57C4"
    private const val pull_tecent_540p ="http://tlive-play.jacocdn.com/tlive/5032563931251409_backup_540P.flv?txSecret=9445a1a78d549ac4201a869f9a013b64&txTime=663F57C4"
    private const val pull_tecent_1080p ="http://tlive-play.jacocdn.com/tlive/5032563931251409_backup.flv?txSecret=a0ff4c6b4ef6009f713a83fe9a3bf5cc&txTime=663F57C4"

    private const val pull_ali_720p ="http://ali-play.jacocdn.com/live/5032563931251409_ali_720P.flv?auth_key=1715427237-0-0-15dcf917baedf4ab6e0cead25d69ed76"
    private const val pull_ali_540p ="http://ali-play.jacocdn.com/live/5032563931251409_ali_540P.flv?auth_key=1715427237-0-0-ff0cddb0f2817870fdf16ada19f32b8d"
    private const val pull_ali_1080p ="http://ali-play.jacocdn.com/live/5032563931251409_ali.flv?auth_key=1715427237-0-0-5967a536a7c941b790a11673457ff41e"

    // cdn 分辨率
    var mBitrateList = arrayOf<String?>("1080p", "720p")

    private const val replaceChannel = "channle"
    private val mAgoraCdnPullList = arrayOf(
        CdnUrlModel("1080p", "${AGORA_PULL_URL}agdemo${replaceChannel}_las1080p.flv"),
        CdnUrlModel("720p", "${AGORA_PULL_URL}agdemo${replaceChannel}.flv"),
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

    private const val urlPre = "agdemo"

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