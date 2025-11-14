package com.example.gestura.model

import android.content.Context
import android.net.Uri

class AslVideoPredictor_ob(
    private val context: Context
) : AutoCloseable {

    private val extractor = HolisticFeatureExtractor(context)
    private val classifier = AslSequenceClassifier(context)

    data class Prediction(
        val label: String,
        val confidence: Float,
        val probabilities: List<Pair<String, Float>>
    )

    fun predictFromVideo(videoUri: Uri): Prediction {
        // 1) video -> [5 * 1662] features
        val seq = extractor.extractSequenceFromVideo(context, videoUri)

        // 2) classify
        val result = classifier.classify(seq)

        return Prediction(
            label = result.label,
            confidence = result.confidence,
            probabilities = result.allProbabilities
        )
    }

    override fun close() {
        extractor.close()
        classifier.close()
    }
}
