// model/AslClassifier.kt
package com.example.gestura.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AslClassifier(
    context: Context,
    tfliteAssetPath: String = "models/asl_classifier.tflite",
    labelsAssetPath: String = "labels/gloss_labels.txt",
    private val windowT: Int = 32
) {
    private val labels = FileUtil.loadLabels(context, labelsAssetPath)
    private val interpreter: Interpreter
    private val inputShape: IntArray

    init {
        val opts = Interpreter.Options()
        val compat = CompatibilityList()
        val delegate = if (compat.isDelegateSupportedOnThisDevice) GpuDelegate(compat.bestOptionsForThisDevice) else null
        if (delegate != null) opts.addDelegate(delegate)

        val model = FileUtil.loadMappedFile(context, tfliteAssetPath)
        interpreter = Interpreter(model, opts)
        inputShape = interpreter.getInputTensor(0).shape() // e.g., [1, T, 75, 3]
    }

    fun close() = interpreter.close()
    fun idToGloss(id: Int) = labels.getOrElse(id) { "UNK" }

    fun inferWindow(seqT75x3: FloatArray): Int {
        val t = inputShape[1]; val l = inputShape[2]; val c = inputShape[3]
        require(l == 75 && c == 3) { "Expected input [1,T,75,3]; got ${inputShape.toList()}" }
        val needed = t * l * c
        val buf = FloatArray(needed) { 0f }
        val copyStart = maxOf(0, needed - seqT75x3.size)
        val srcStart = maxOf(0, seqT75x3.size - needed)
        if (seqT75x3.isNotEmpty())
            System.arraycopy(seqT75x3, srcStart, buf, copyStart, minOf(seqT75x3.size, needed))

        val bb: ByteBuffer = ByteBuffer.allocateDirect((4L * needed).toInt()).order(ByteOrder.LITTLE_ENDIAN)
        buf.forEach { bb.putFloat(it) }
        bb.rewind()

        val outShape = interpreter.getOutputTensor(0).shape() // [1,G]
        val g = outShape[1]
        val out = Array(1) { FloatArray(g) }
        interpreter.run(bb, out)
        val logits = out[0]
        var best = 0; var bestVal = logits[0]
        for (i in 1 until g) if (logits[i] > bestVal) { bestVal = logits[i]; best = i }
        return best
    }
}
