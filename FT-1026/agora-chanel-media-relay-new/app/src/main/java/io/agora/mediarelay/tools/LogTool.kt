package io.agora.mediarelay.tools

import android.util.Log
import androidx.annotation.StringRes
import com.blankj.utilcode.util.StringUtils

/**
 * @author create by zhangwei03
 */
object LogTool {

    private const val TAG = "MediaRelay"

    @JvmStatic
    fun d(message: String) {
        Log.d(TAG, message)
    }

    @JvmStatic
    fun d(@StringRes stringRes: Int) {
        Log.d(TAG, StringUtils.getString(stringRes))
    }

    @JvmStatic
    fun e(message: String) {
        Log.e(TAG, message)
    }

    @JvmStatic
    fun e(@StringRes stringRes: Int) {
        Log.e(TAG, StringUtils.getString(stringRes))
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    @JvmStatic
    fun d(tag: String, @StringRes stringRes: Int) {
        Log.d(tag, StringUtils.getString(stringRes))
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }

    @JvmStatic
    fun e(tag: String, @StringRes stringRes: Int) {
        Log.e(tag, StringUtils.getString(stringRes))
    }
}