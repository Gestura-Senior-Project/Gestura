// repo/LandmarkRepo.kt
package com.example.gestura.repo

import android.content.Context
import android.net.Uri
import com.example.gestura.network.ApiClient
import com.example.gestura.network.LandmarkApi
import com.example.gestura.network.LandmarkPayload
import com.example.gestura.util.Decompress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class LandmarkRepo(private val api: LandmarkApi) {
    suspend fun uploadAndGetLandmarks(ctx: Context, uri: Uri): Triple<FloatArray, Int, Double> {
        return withContext(Dispatchers.IO) {
            val cr = ctx.contentResolver
            val mime = cr.getType(uri) ?: "video/mp4"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read video")
            val part = MultipartBody.Part.createFormData(
                "file", "video.${mime.substringAfterLast('/')}",
                bytes.toRequestBody(mime.toMediaType())
            )
            val res = api.extract(part)
            if (!res.isSuccessful || res.body() == null) error("Server error ${res.code()}: ${res.errorBody()?.string()}")
            val body: LandmarkPayload = res.body()!!
            val shape = body.shape // [T,75,3]
            val T = shape[0]
            val count = shape[0]*shape[1]*shape[2]
            val fa = Decompress.decodeFloatArray(body.array_b64, count)
            Triple(fa, T, body.fps)
        }
    }
}
