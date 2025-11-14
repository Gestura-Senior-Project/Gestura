package com.example.gestura.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AslSequenceClassifier(
    context: Context,
    modelAssetPath: String = "asl_model.tflite",
    labelsAssetPath: String = "gloss_lables.txt"
) {

    companion object {
        private const val TIMESTEPS = 5
        private const val FEATURES_PER_FRAME = 1662
        private const val NUM_CLASSES = 13
    }

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val assetManager = context.assets

        // Load TFLite model
        val modelBuffer = assetManager.open(modelAssetPath).use { input ->
            val bytes = input.readBytes()
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }

        interpreter = Interpreter(modelBuffer)

        // Load labels
        labels = assetManager.open(labelsAssetPath).use { input ->
            input.bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }

        require(labels.size == NUM_CLASSES) {
            "Expected $NUM_CLASSES labels, but found ${labels.size}"
        }
    }

    data class Result(
        val label: String,
        val confidence: Float,
        val allProbabilities: List<Pair<String, Float>>
    )

    /**
     * sequence = flattened [TIMESTEPS, FEATURES_PER_FRAME] -> size = 5 * 1662
     */
    fun classify(sequence: FloatArray): Result {
        require(sequence.size == TIMESTEPS * FEATURES_PER_FRAME) {
            "Expected input of size ${TIMESTEPS * FEATURES_PER_FRAME}, got ${sequence.size}"
        }

        // Prepare input: [1, 5, 1662]
        val input = Array(1) {
            Array(TIMESTEPS) {
                FloatArray(FEATURES_PER_FRAME)
            }
        }

        var idx = 0
        for (t in 0 until TIMESTEPS) {
            for (f in 0 until FEATURES_PER_FRAME) {
                input[0][t][f] = sequence[idx++]
            }
        }

        // Output: [1, 13]
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        interpreter.run(input, output)

        val probs = output[0]
        var bestIdx = 0
        var bestProb = probs[0]
        for (i in 1 until NUM_CLASSES) {
            if (probs[i] > bestProb) {
                bestProb = probs[i]
                bestIdx = i
            }
        }

        val bestLabel = labels[bestIdx]

        val all = probs.mapIndexed { i, p -> labels[i] to p }

        return Result(
            label = bestLabel,
            confidence = bestProb,
            allProbabilities = all
        )
    }

    fun close() {
        interpreter.close()
    }
}
