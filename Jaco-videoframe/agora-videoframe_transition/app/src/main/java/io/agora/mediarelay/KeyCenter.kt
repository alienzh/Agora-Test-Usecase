package io.agora.mediarelay

import kotlin.random.Random

const val HOST: Int = 2
const val BROADCASTER: Int = 1
const val AUDIENCE: Int = 0

/**
 * @author create by zhangwei03
 */
object KeyCenter {

    val rtcUid :Int by lazy {
        Random(System.nanoTime()).nextInt(10000) + 1000000
    }
}