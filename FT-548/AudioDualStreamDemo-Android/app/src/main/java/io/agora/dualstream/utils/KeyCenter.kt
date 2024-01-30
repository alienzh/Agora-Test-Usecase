package io.agora.dualstream.utils

import kotlin.random.Random

object KeyCenter {

    private const val TAG = "KeyCenter"

    val rtcUid: Int = Random(System.nanoTime()).nextInt(10000) + 1000000
}