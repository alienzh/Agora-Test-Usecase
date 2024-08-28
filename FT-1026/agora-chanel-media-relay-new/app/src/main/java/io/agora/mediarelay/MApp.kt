package io.agora.mediarelay

import android.app.Application
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.Utils
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.agora.logging.FileLogger
import io.agora.logging.LogManager
import org.json.JSONObject
import java.io.IOException

/**
 * @author create by zhangwei03
 */
class MApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
        GsonUtils.setGsonDelegate(gsonDelegate)
        LogManager.instance().addLogger(
            FileLogger(
                getExternalFilesDir(null)!!.path,
                "agorademo",
                (1024 * 1024).toLong(),
                3,
            )
        )
    }

    private val gsonDelegate = GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .registerTypeAdapter(TypeToken.get(JSONObject::class.java).type, object : TypeAdapter<JSONObject?>() {
            @Throws(IOException::class)
            override fun write(out: JsonWriter, value: JSONObject?) {
                out.jsonValue(value.toString())
            }

            @Throws(IOException::class)
            override fun read(`in`: JsonReader): JSONObject? {
                return null
            }
        })
        .enableComplexMapKeySerialization()
        .disableHtmlEscaping()
        .create()
}