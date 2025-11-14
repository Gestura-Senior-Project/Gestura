// network/ApiClient.kt
package com.example.gestura.network

import com.example.gestura.network.LandmarkApi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClient {
    fun landmarks(baseUrl: String): LandmarkApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(LandmarkApi::class.java)
}
