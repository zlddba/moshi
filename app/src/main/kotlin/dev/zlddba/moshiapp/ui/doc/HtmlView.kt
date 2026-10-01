package dev.zlddba.moshiapp.ui.doc

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

private const val MIN_HEIGHT = 240
private const val MAX_HEIGHT = 20000
private const val HEIGHT_BRIDGE = "AndroidHeight"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlView(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Int = MIN_HEIGHT,
    maxHeight: Int = MAX_HEIGHT
) {
    var measured by remember(html) { mutableIntStateOf(minHeight) }
    var loaded by remember { mutableStateOf<String?>(null) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(Color.TRANSPARENT)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onHeight(value: Int) {
                            measured = value.coerceIn(minHeight, maxHeight)
                        }
                    },
                    HEIGHT_BRIDGE
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "var h=(typeof window.__measure==='function')" +
                                "?window.__measure():document.body.scrollHeight;" +
                                "$HEIGHT_BRIDGE.onHeight(h);",
                            null
                        )
                    }
                }
            }
        },
        update = { webView ->
            if (loaded != html) {
                loaded = html
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = measured.dp, max = maxHeight.dp)
    )
}
