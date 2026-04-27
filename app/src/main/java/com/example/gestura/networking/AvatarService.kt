package com.example.gestura.networking

import com.example.gestura.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AvatarService {

    private val client = OkHttpClient()

    // Using the GENASL API base from your build config
    private val baseUrl = BuildConfig.GENASL_API_BASE

    interface AvatarCallback {
        fun onSuccess(videoUrl: String)
        fun onError(message: String)
    }

    /**
     * Sends text to the GenASL API to generate an ASL animation video.
     */
    fun generateAvatar(text: String, callback: AvatarCallback) {
        val json = JSONObject().apply {
            put("text", text)
            // Additional parameters based on AWS GenASL implementation
            put("style", "realistic")
        }

        val request = Request.Builder()
            .url("$baseUrl/generate-asl")
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .header("x-api-key", BuildConfig.GENASL_API_KEY)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val resJson = JSONObject(body)
                    // GenASL usually returns a S3 URL or a direct video link
                    val videoUrl = resJson.optString("video_url")
                    if (videoUrl.isNotEmpty()) {
                        callback.onSuccess(videoUrl)
                    } else {
                        callback.onError("Failed to get video URL")
                    }
                } else {
                    callback.onError("Server error: ${response.code}")
                }
            }
        })
    }
}
