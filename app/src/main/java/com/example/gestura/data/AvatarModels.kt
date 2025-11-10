package com.example.gestura.data

// Body you send to POST /avatar
data class AvatarRequest(
    val text: String,
    val language: String = "en",
    val speed: String = "normal",
    val style: String = "neutral",
    val s3Key: String? = null   // optional: exact S3 key if you have it
)

// Response you expect back from Lambda
data class AvatarResponse(
    val videoUrl: String,
    val key: String? = null
)
