package com.example.gestura.networking

import android.graphics.Bitmap
import android.util.Base64
import com.example.gestura.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class LiveAslServerClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val openAiClient = OkHttpClient()

    companion object {
        // Updated to Port 5001 for Live Prediction
        private const val SERVER_URL = "http://10.0.2.2:5001/predict_live"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
    }

    /**
     * Sends a single frame (Bitmap) to the server for live gloss prediction.
     */
    fun streamFrame(bitmap: Bitmap, onResult: (String?) -> Unit) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()
        
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("frame", "frame.jpg", byteArray.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(SERVER_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val json = JSONObject(bodyStr)
                    onResult(json.optString("gloss", ""))
                } else {
                    onResult(null)
                }
            }
        })
    }

    /**
     * Finalizes the session by sending all collected glosses to OpenAI to form a coherent sentence.
     */
    fun finalizeSentence(glosses: List<String>, onFinalResult: (String) -> Unit) {
        if (glosses.isEmpty()) {
            onFinalResult("")
            return
        }

        val prompt = "Turn the following ASL glosses into a natural English sentence: ${glosses.joinToString(" ")}"
        
        val json = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(OPENAI_URL)
            .header("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body)
            .build()

        openAiClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onFinalResult(glosses.joinToString(" "))
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val resJson = JSONObject(bodyStr)
                    val choices = resJson.getJSONArray("choices")
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    onFinalResult(content.trim())
                } else {
                    onFinalResult(glosses.joinToString(" "))
                }
            }
        })
    }
}
