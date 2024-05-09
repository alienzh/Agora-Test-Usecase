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
    Ali
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

    private const val push_huawei =
        "rtmp://push-hw.jacocdn.com/live/5032135167930818_main?hwSecret=1f77a692ea3da3443386db91df8bf6bc065172b11610b578489c6bcf0db3ed37&hwTime=663dc5dd"
    private const val push_tecent =
        "rtmp://tlive-push.jacocdn.com/tlive/5032135167930818_backup?txSecret=aa720aba117e0f411ac955ec1eb58bfd&txTime=663DC5DF"
    private const val push_ali =
        "rtmp://ali-push.jacocdn.com/live/5032135167930818_ali?auth_key=1715324384-0-0-b142a3281b6f95dd1b18fbabe2107f7c"

    private const val pull_huawei_720p =
        "http://play-hw.jacocdn.com/live/5032135167930818_main_720P.flv?hwSecret=4c29d424229add9159971e70153845c8fc8f8deabba3b24461a7765e0de8e48c&hwTime=663dc6e1"
    private const val pull_huawei_540p =
        "http://play-hw.jacocdn.com/live/5032135167930818_main_540P.flv?hwSecret=42fb8837b25a43d44c1e60272248673857a247790bd69a3761bb240093420e7c&hwTime=663dc6e1"
    private const val pull_huawei_1080p =
        "http://play-hw.jacocdn.com/live/5032135167930818_main.flv?hwSecret=81bd19c9cc153828f8d2f1dfd296d66fb77fba558f61e9c83289f8b291bca7dc&hwTime=663dc6e1"

    private const val pull_tecent_720p =
        "http://tlive-play.jacocdn.com/tlive/5032135167930818_backup_720P.flv?txSecret=d810b0b0d4c773a64a3fa0c5d49d8156&txTime=663DC5E8"
    private const val pull_tecent_540p =
        "http://tlive-play.jacocdn.com/tlive/5032135167930818_backup_540P.flv?txSecret=9f4e81ae7a14b1ca1fa74406a485683d&txTime=663DC5E8"
    private const val pull_tecent_1080p =
        "http://tlive-play.jacocdn.com/tlive/5032135167930818_backup.flv?txSecret=74290305f05e889861d901b348da0ed6&txTime=663DC5E8"

    private const val pull_ali_720p =
        "http://ali-play.jacocdn.com/live/5032135167930818_ali_720P.flv?auth_key=1715324641-0-0-2c135bf40a89a1e06894730bc70ae906"
    private const val pull_ali_540p =
        "http://ali-play.jacocdn.com/live/5032135167930818_ali_540P.flv?auth_key=1715324641-0-0-42763e87b0cb8873f12eec27f451838f\""
    private const val pull_ali_1080p =
        "http://ali-play.jacocdn.com/live/5032135167930818_ali.flv?auth_key=1715324641-0-0-2c942e3bc5a3877861f89e41998c178d"

    // cdn 分辨率
    var mBitrateList = arrayOf<String?>("720p", "1080p")

    private const val replaceChannel = "channle"
    private val mAgoraCdnPullList = arrayOf(
        CdnUrlModel("1080p", "${AGORA_PULL_URL}agdemo${replaceChannel}_las1080p.flv"),
        CdnUrlModel("720p", "$$AGORA_PULL_URL}agdemo${replaceChannel}.flv"),
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