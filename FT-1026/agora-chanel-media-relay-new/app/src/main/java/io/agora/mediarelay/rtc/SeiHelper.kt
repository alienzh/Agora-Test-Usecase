package io.agora.mediarelay.rtc

import io.agora.mediarelay.tools.TimeUtils

object SeiHelper {

    fun buildSei(channelId: String, uid: Int): Map<String, Any> {
        val streamList = mutableListOf<Map<String, Any>>()
        streamList.add(
            mapOf(
                "uid" to uid,
                "paneid" to 0,
                "zorder" to 0,
                "x" to 0,
                "y" to 0,
                "w" to 0,
                "h" to 0,
                "type" to 0,
                "status" to 1,
                "cameraDisabled" to 0,
                "muted" to 0,
                "vol" to 165,
                "vad" to 139,
                "netQuality" to 0
            )
        )
        return mapOf(
            "canvas" to mapOf(
                "w" to 1080,
                "h" to 1920,
                "bgnd" to 0
            ),
            "stream" to streamList,
            "ver" to "1.0.0.20220915",
            "ts" to TimeUtils.currentTimeMillis(),
            "source" to "aliyun",
            "info" to mapOf(
                "mcu_room_state" to mapOf(
                    "canvas" to mapOf(
                        "w" to 1080,
                        "h" to 1920
                    )
                ),
                "img_uri_prefix" to "http:\\/\\/img.hektarapp.io\\/orj360\\/",
                "layout_mode" to 0,
                "layout_numbers" to mutableListOf(1),
                "pk_mode" to 0,
                "regions" to mutableListOf(
                    mapOf(
                        "icon" to "3b9ae203la1hdmgih996ej20yi0p0dfz.jpg",
                        "multi_pk_escaped" to false,
                        "mute" to false,
                        "name" to "Yc39",
                        "uid" to uid
                    )
                ),
                "rid" to "6d78fdc6-1e6b-4a47-8a0e-cffc89b5e510"
            )
        )
    }
}
