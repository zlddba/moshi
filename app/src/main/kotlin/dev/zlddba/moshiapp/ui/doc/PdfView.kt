package dev.zlddba.moshiapp.ui.doc

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RENDER_WIDTH = 1080
private const val PLACEHOLDER_HEIGHT = 420

private data class PdfInfo(
    val pageCount: Int,
    val ratios: List<Float>
)

/**
 * 逐页渲染 PDF。这里刻意用普通 Column 而不是 LazyColumn：
 * 本组件会被放进详情页的 LazyColumn 当 item，嵌套的可滚动容器在 item 内
 * 会被以无限高度约束测量并抛 IllegalStateException，因此滚动交给外层。
 */
@Composable
fun PdfView(file: File, modifier: Modifier = Modifier) {
    var info by remember(file.path) { mutableStateOf<PdfInfo?>(null) }

    LaunchedEffect(file.path) {
        info = withContext(Dispatchers.IO) { readInfo(file) }
    }

    val loaded = info ?: run {
        LoadingBox(modifier)
        return
    }

    if (loaded.pageCount <= 0) {
        Text(
            text = "无法渲染该 PDF 文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        for (index in 0 until loaded.pageCount) {
            PdfPageItem(
                file = file,
                index = index,
                ratio = loaded.ratios.getOrNull(index) ?: DEFAULT_RATIO
            )
        }
    }
}

@Composable
private fun PdfPageItem(file: File, index: Int, ratio: Float) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file.path, index) {
        value = withContext(Dispatchers.IO) { renderPage(file, index) }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        val rendered = bitmap
        if (rendered == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLACEHOLDER_HEIGHT.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = "第 ${index + 1} 页",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((BASELINE_WIDTH_DP * ratio).dp)
            )
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private fun readInfo(file: File): PdfInfo {
    if (!file.isFile) return PdfInfo(0, emptyList())
    val descriptor = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: Exception) {
        return PdfInfo(0, emptyList())
    }
    return descriptor.use { fd ->
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: Exception) {
            return@use PdfInfo(0, emptyList())
        }
        renderer.use { pdf ->
            val ratios = ArrayList<Float>(pdf.pageCount)
            for (index in 0 until pdf.pageCount) {
                val ratio = try {
                    pdf.openPage(index).use { page ->
                        if (page.width <= 0) DEFAULT_RATIO
                        else page.height.toFloat() / page.width.toFloat()
                    }
                } catch (e: Exception) {
                    DEFAULT_RATIO
                }
                ratios.add(ratio)
            }
            PdfInfo(pdf.pageCount, ratios)
        }
    }
}

private fun renderPage(file: File, index: Int): Bitmap? {
    if (!file.isFile) return null
    val descriptor = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: Exception) {
        return null
    }
    return descriptor.use { fd ->
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: Exception) {
            return@use null
        }
        renderer.use { pdf ->
            if (index !in 0 until pdf.pageCount) return@use null
            val page = try {
                pdf.openPage(index)
            } catch (e: Exception) {
                return@use null
            }
            page.use { current ->
                val ratio = if (current.width <= 0) {
                    DEFAULT_RATIO
                } else {
                    current.height.toFloat() / current.width.toFloat()
                }
                val height = (RENDER_WIDTH * ratio).toInt().coerceAtLeast(1)
                val bitmap = try {
                    Bitmap.createBitmap(RENDER_WIDTH, height, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    return@use null
                }
                bitmap.eraseColor(android.graphics.Color.WHITE)
                try {
                    current.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (e: Exception) {
                    bitmap.recycle()
                    null
                }
            }
        }
    }
}

private const val DEFAULT_RATIO = 1.414f
private const val BASELINE_WIDTH_DP = 380f
