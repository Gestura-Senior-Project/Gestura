package com.example.gestura.contribute

import android.content.Context
import android.net.Uri
import com.example.gestura.model.AslSequenceClassifier
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AslSamplePipeline(
    private val context: Context
) : AutoCloseable {

    companion object {
        private const val TIMESTEPS = 5
        private const val FEATURES_PER_FRAME = 1662

        // Same as your Python FastAPI server
        private const val HOLISTIC_BASE_URL = "http://10.0.2.2:8000"
        private const val EXTRACT_PATH = "/extract"
    }

    private val classifier = AslSequenceClassifier(context)
    private val httpClient = OkHttpClient()

    data class SampleResult(
        val word: String,
        val predictedLabel: String,
        val confidence: Float,   // 0–100
        val keypoints: FloatArray
    )

    fun run(word: String, videoUri: Uri): SampleResult {
        // 1) Get features from FastAPI + MediaPipe
        val features = uploadVideoAndGetFeatures(videoUri)

        if (features.size != TIMESTEPS * FEATURES_PER_FRAME) {
            throw IllegalStateException(
                "Expected ${TIMESTEPS * FEATURES_PER_FRAME} " +
                        "features, got ${features.size}"
            )
        }

        // 2) Classify locally
        val result = classifier.classify(features)

        return SampleResult(
            word = word,
            predictedLabel = result.label,
            confidence = result.confidence * 100f,
            keypoints = features
        )
    }

    private fun uploadVideoAndGetFeatures(uri: Uri): FloatArray {
        val bytes = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes() }
            ?: throw IllegalStateException(
                "Unable to open video input stream"
            )

        val mediaType = "video/mp4".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "video",
                filename = "upload.mp4",
                body = bytes.toRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url(HOLISTIC_BASE_URL + EXTRACT_PATH)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val txt = response.body?.string()
            throw IllegalStateException(
                "Holistic server error: HTTP " +
                        "${response.code} - $txt"
            )
        }

        val bodyStr = response.body?.string()
            ?: throw IllegalStateException("Empty response")

        val json = JSONObject(bodyStr)
        if (!json.has("features")) {
            throw IllegalStateException(
                "Response missing 'features'"
            )
        }

        val arr = json.getJSONArray("features")
        val out = FloatArray(arr.length())
        for (i in 0 until arr.length()) {
            out[i] = arr.getDouble(i).toFloat()
        }
        return out
    }

    override fun close() {
        classifier.close()
    }
}
