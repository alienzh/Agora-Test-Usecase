package io.agora.mediarelay.tools

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.agora.mediarelay.MApp

/**
 * @author create by zhangwei03
 */
object ToastTool {

    fun showToast(resStringId: Int) {
        runOnMainThread {
            Toast.makeText(MApp.instance(), resStringId, Toast.LENGTH_LONG).show()
        }
    }

    fun showToast(str: String?) {
        runOnMainThread {
            Toast.makeText(MApp.instance(), str, Toast.LENGTH_LONG).show()
        }
    }

    private var mainHandler: Handler? = null

    private fun runOnMainThread(runnable: Runnable) {
        if (mainHandler == null) {
            mainHandler = Handler(Looper.getMainLooper())
        }
        mainHandler?.let {
            if (Thread.currentThread() === it.looper.thread) {
                runnable.run()
            } else {
                it.post(runnable)
            }
        }
    }
}