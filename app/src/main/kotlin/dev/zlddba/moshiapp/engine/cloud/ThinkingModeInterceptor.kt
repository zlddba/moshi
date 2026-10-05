package dev.zlddba.moshiapp.engine.cloud

import android.util.Log
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject

class ThinkingModeInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.toString().contains(DEEPSEEK_HOST)) return chain.proceed(request)
        val body = request.body ?: return chain.proceed(request)
        val contentType = body.contentType() ?: return chain.proceed(request)
        if (!contentType.subtype.equals(JSON_SUBTYPE, ignoreCase = true)) {
            return chain.proceed(request)
        }
        val raw = try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Throwable) {
            Log.w(TAG, "read request body failed, sending original", e)
            return chain.proceed(request)
        }
        if (raw.contains(THINKING_KEY)) return chain.proceed(request)
        val patched = try {
            inject(raw)
        } catch (e: Throwable) {
            Log.w(TAG, "inject thinking field failed, sending original", e)
            return chain.proceed(request)
        }
        Log.d(TAG, "thinking mode disabled url=${request.url}")
        return chain.proceed(
            request.newBuilder()
                .method(request.method, patched.toRequestBody(contentType))
                .build()
        )
    }

    private fun inject(raw: String): String {
        val root = JSONObject(raw)
        val thinking = JSONObject()
        thinking.put("type", "disabled")
        root.put(THINKING_KEY_NAME, thinking)
        return root.toString()
    }

    private companion object {
        const val TAG = "MoshiCloud"
        const val DEEPSEEK_HOST = "deepseek"
        const val THINKING_KEY_NAME = "thinking"
        const val THINKING_KEY = "\"$THINKING_KEY_NAME\""
        const val JSON_SUBTYPE = "json"
    }
}
