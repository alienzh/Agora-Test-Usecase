package io.agora.mediarelay.widget

import android.util.Log
import android.view.View
import com.blankj.utilcode.util.ToastUtils

abstract class OnFastClickListener constructor(val delay: Long = 1500L, val message: String? = null) :
    View.OnClickListener {

    private var lastClickTime: Long = 0L

    override fun onClick(v: View) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= delay) {
            // 执行点击操作
            lastClickTime = currentTime
            onClickJacking(v)
        } else {
            message?.let {
                ToastUtils.showShort(it)
            }
            Log.d("OnFastClickListener", "Click time is too short")
        }
    }

    abstract fun onClickJacking(view: View)
}