// pipeline/SequenceRunner.kt
package com.example.gestura.pipeline

import com.example.gestura.model.AslClassifier
import kotlin.math.min

object SequenceRunner {
    /**
     * landmarks: FloatArray of length T*75*3 (row-major, frame by frame).
     * shape: [T,75,3]
     * windowT, hopT: in frames
     */
    fun run(
        classifier: AslClassifier,
        landmarks: FloatArray,
        T: Int,
        windowT: Int = 32,
        hopT: Int = 16
    ): List<Int> {
        val frameSize = 75*3
        val out = ArrayList<Int>()
        var t = 0
        while (t < T) {
            val end = min(t + windowT, T)
            val len = end - t
            // copy window frames into contiguous float array [len*75*3]
            val window = FloatArray(len*frameSize)
            val srcOff = t*frameSize
            System.arraycopy(landmarks, srcOff, window, 0, len*frameSize)
            val id = classifier.inferWindow(window)
            out.add(id)
            t += hopT
        }
        return out
    }
}
