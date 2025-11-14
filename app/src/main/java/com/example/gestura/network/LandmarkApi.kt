// network/LandmarkApi.kt
package com.example.gestura.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class LandmarkPayload(
    val shape: List<Int>,     // [T,75,3]
    val dtype: String,        // "float32"
    val fps: Double,
    val array_b64: String     // gzip(base64(raw float32 bytes))
)

interface LandmarkApi {
    @Multipart
    @POST("/api/extract-landmarks")
    suspend fun extract(
        @Part file: MultipartBody.Part
    ): Response<LandmarkPayload>
}
