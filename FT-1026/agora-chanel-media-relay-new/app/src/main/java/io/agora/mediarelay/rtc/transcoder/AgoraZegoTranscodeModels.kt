package io.agora.mediarelay.rtc.transcoder

import im.zego.zegoexpress.entity.ZegoUser

data class AgoraTransInputItem constructor(
    val rtcChannel: String,
    val rtcUid: Int,
    val rtcAccount: String,
)

data class AgoraTransOutItem constructor(
    val zegoAppId: Long,
    val zegoAppSign: String,
    val zegoRoomId: String,
    val robotUserId: String,
    val robotUserName: String,
    val streamId: String,
)

data class AgoraTranscodeSetting constructor(
    val enableUserAccount: Boolean,
    val rtcStringUid: String,
    val inputItems: List<AgoraTransInputItem>,
    val outputItems: List<AgoraTransOutItem>,
) {

    companion object {
        fun cloudAgoraTranscoding(
            enableUserAccount: Boolean,
            zegoAppId: Long,
            zegoAppSign: String,
            zegoRoomId: String,
            zegoUser: ZegoUser,
            zegoPublishStreamId: String,
            channelUid: ChannelUid,
        ): AgoraTranscodeSetting {
            val inputItems = mutableListOf<AgoraTransInputItem>()
            val item = AgoraTransInputItem(
                rtcChannel = channelUid.channel,
                rtcUid = channelUid.uid,
                rtcAccount = channelUid.stringUid
            )
            inputItems.add(item)
            val output = AgoraTransOutItem(
                zegoAppId = zegoAppId,
                zegoAppSign = zegoAppSign,
                zegoRoomId = zegoRoomId,
                robotUserId = zegoUser.userID,
                robotUserName = zegoUser.userName,
                streamId = zegoPublishStreamId
            )
            val outputItems = mutableListOf<AgoraTransOutItem>()
            outputItems.add(output)
            return AgoraTranscodeSetting(enableUserAccount, channelUid.stringUid, inputItems, outputItems)
        }
    }
}

data class ZegoTransInputItem constructor(
    val zegoAppId: Long,
    val zegoAppSign: String,
    val zegoRoomId: String,
    val robotUserId: String,
    val robotUserName: String,
    val streamId: String,
)

data class ZegoTransOutItem constructor(
    val rtcChannel: String,
    val rtcStringUid: String,
)

data class ZegoTranscodeSetting constructor(
    val enableUserAccount: Boolean,
    val rtcStringUid: String,
    val inputItems: List<ZegoTransInputItem>,
    val outputItems: List<ZegoTransOutItem>,
) {

    companion object {
        fun cloudZegoTranscoding(
            enableUserAccount: Boolean,
            rtcStringUid: String,
            zegoAppId: Long,
            zegoAppSign: String,
            zegoRoomId: String,
            zegoUser: ZegoUser,
            zegoPublishStreamId: String,
            agoraChannel: ChannelUid,
        ): ZegoTranscodeSetting {
            val inputItems = mutableListOf<ZegoTransInputItem>()
            val item = ZegoTransInputItem(
                zegoAppId = zegoAppId,
                zegoAppSign = zegoAppSign,
                zegoRoomId = zegoRoomId,
                robotUserId = zegoUser.userID + "_robot",
                robotUserName = zegoUser.userName + "_robot",
                streamId = zegoPublishStreamId
            )
            inputItems.add(item)
            val output = ZegoTransOutItem(
                rtcChannel = agoraChannel.channel,
                rtcStringUid = agoraChannel.stringUid,
            )
            val outputItems = mutableListOf<ZegoTransOutItem>()
            outputItems.add(output)
            return ZegoTranscodeSetting(enableUserAccount, rtcStringUid, inputItems, outputItems)
        }
    }
}