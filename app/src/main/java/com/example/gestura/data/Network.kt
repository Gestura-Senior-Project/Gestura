package com.example.gestura.data

import com.example.gestura.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object Network {
    val api: AvatarApi by lazy {
        val client = OkHttpClient.Builder().build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.GENASL_API_BASE) // e.g., https://n3ym8pji0m.execute-api.us-east-1.amazonaws.com/
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
            .create(AvatarApi::class.java)
    }
}

