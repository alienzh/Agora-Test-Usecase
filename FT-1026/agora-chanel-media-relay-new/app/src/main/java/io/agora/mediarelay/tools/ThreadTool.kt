package io.agora.mediarelay.tools

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

object ThreadTool {
    val scheduledThreadPool: ScheduledExecutorService = Executors.newScheduledThreadPool(5)
}