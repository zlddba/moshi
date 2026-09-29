package dev.zlddba.moshiapp.ingest.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrTextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(context: Context, uri: Uri): String {
        val input = withContext(Dispatchers.IO) {
            InputImage.fromFilePath(context, uri)
        }
        return await(input)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): String {
        return await(InputImage.fromBitmap(bitmap, 0))
    }

    private suspend fun await(input: InputImage): String {
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(input)
                .addOnSuccessListener { text -> continuation.resume(text.text) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }
}
