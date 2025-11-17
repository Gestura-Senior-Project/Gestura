package com.example.gestura.model

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AslVideoPredictor(
    private val context: Context
) : AutoCloseable {

    companion object {
        // Must match server
        private const val TIMESTEPS = 5
        private const val FEATURES_PER_FRAME = 1662

        // TODO: replace with your actual Render URL (no trailing slash)
        private const val HOLISTIC_BASE_URL = "http://10.0.2.2:8000"
        private const val EXTRACT_PATH = "/extract"
    }

    private val classifier = AslSequenceClassifier(context)
    private val httpClient = OkHttpClient()

    data class Prediction(
        val label: String,
        val confidence: Float,
        val probabilities: List<Pair<String, Float>>
    )

    /**
     * Upload video to Render, get back [TIMESTEPS * FEATURES_PER_FRAME] features,
     * run on-device classifier, and return prediction.
     *
     * This is a blocking call, but OnDeviceCaptionFragment already runs it
     * inside Dispatchers.IO, so it's safe to keep it synchronous.
     */
    fun predictFromVideo(videoUri: Uri): Prediction {
        // 1) Call server to get features
        val seq = uploadVideoAndGetFeatures(videoUri)

        // Sanity check
        if (seq.size != TIMESTEPS * FEATURES_PER_FRAME) {
            throw IllegalStateException(
                "Expected ${TIMESTEPS * FEATURES_PER_FRAME} features, got ${seq.size}"
            )
        }

        // 2) Classify locally with TFLite
        val result = classifier.classify(seq)

        return Prediction(
            label = result.label,
            confidence = result.confidence,
            probabilities = result.allProbabilities

        )
    }

    /**
     * Uploads the video to the Render Holistic server and parses the returned
     * feature vector into a FloatArray.
     */
    private fun uploadVideoAndGetFeatures(uri: Uri): FloatArray {
        // Read entire video into memory (fine for short clips).
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalStateException("Unable to open video input stream")

        val mediaType = "video/mp4".toMediaType()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "video",
                filename = "upload.mp4",
                body = bytes.toRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url(HOLISTIC_BASE_URL + EXTRACT_PATH)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val bodyText = response.body?.string()
            throw IllegalStateException(
                "Holistic server error: HTTP ${response.code} - $bodyText"
            )
        }

        val bodyString = response.body?.string()
            ?: throw IllegalStateException("Empty response from holistic server")

        val json = JSONObject(bodyString)
        if (!json.has("features")) {
            throw IllegalStateException("Response missing 'features' field")
        }

        val featuresJson = json.getJSONArray("features")
        val result = FloatArray(featuresJson.length())
        for (i in 0 until featuresJson.length()) {
            result[i] = featuresJson.getDouble(i).toFloat()
        }

        return result
    }

    override fun close() {
        // Nothing to close on the HTTP client
        classifier.close()
    }
}
