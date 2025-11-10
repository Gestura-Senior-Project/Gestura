package com.example.gestura.data

import retrofit2.http.Body
import retrofit2.http.POST

interface AvatarApi {
    @POST("avatar")
    suspend fun generate(@Body req: AvatarRequest): AvatarResponse
}