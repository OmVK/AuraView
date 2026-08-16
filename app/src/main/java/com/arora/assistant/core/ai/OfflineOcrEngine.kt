package com.arora.assistant.core.ai

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrWord(val text: String, val boundingBox: Rect?)
data class OcrLine(val text: String, val boundingBox: Rect?, val words: List<OcrWord>)
data class OcrResult(val fullText: String, val lines: List<OcrLine>)

object OfflineOcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText: Text ->
                val lines = mutableListOf<OcrLine>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val words = line.elements.map { OcrWord(it.text, it.boundingBox) }
                        lines.add(OcrLine(line.text, line.boundingBox, words))
                    }
                }
                continuation.resume(OcrResult(visionText.text, lines))
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
}
