package io.agora.mediarelay.tools

import android.content.Context
import android.content.res.AssetManager
import android.text.TextUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.Utils
import io.agora.mediarelay.MApp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object FileUtils {
    val baseStrPath: String
        get() = Utils.getApp().getExternalFilesDir("")!!.absolutePath + File.separator + "image"

    val blackImage:String
        get() = baseStrPath + File.separator + "black.png"

    val SEPARATOR = File.separator

    fun copyFileFromAssets(context: Context, assetsFilePath: String, localStoragePath: String?=null): String? {
        var storagePath = localStoragePath?: baseStrPath
        if (TextUtils.isEmpty(storagePath)) {
            return null
        } else if (storagePath.endsWith(SEPARATOR)) {
            storagePath = storagePath.substring(0, storagePath.length - 1)
        }
        if (TextUtils.isEmpty(assetsFilePath) || assetsFilePath.endsWith(SEPARATOR)) {
            return null
        }
        val storageFilePath = storagePath + SEPARATOR + assetsFilePath
        val assetManager: AssetManager = context.assets
        try {
            val file = File(storageFilePath)
            if (file.exists()) return storageFilePath
            file.getParentFile()?.mkdirs()
            val inputStream: InputStream = assetManager.open(assetsFilePath)
            readInputStream(storageFilePath, inputStream)
        } catch (e: IOException) {
            return null
        }

        return storageFilePath
    }

    /**
     * 读取输入流中的数据写入输出流
     *
     * @param storagePath 目标文件路径
     * @param inputStream 输入流
     */
    private fun readInputStream(storagePath: String, inputStream: InputStream) {
        val file = File(storagePath)
        try {
            if (!file.exists()) {
                // 1.建立通道对象
                val fos = FileOutputStream(file)
                // 2.定义存储空间
                val buffer = ByteArray(inputStream.available())
                // 3.开始读文件
                var lenght = 0
                while (inputStream.read(buffer).also { lenght = it } != -1) { // 循环从输入流读取buffer字节
                    // 将Buffer中的数据写到outputStream对象中
                    fos.write(buffer, 0, lenght)
                }
                fos.flush() // 刷新缓冲区
                // 4.关闭流
                fos.close()
                inputStream.close()
            }
        } catch (e: IOException) {
            LogTool.e("FileUtils", e.toString())
        }
    }

    fun deleteFile(filePath: String?): Boolean {
        val file = File(filePath)
        return if (file.exists()) {
            file.delete()
        } else false
    }
}
