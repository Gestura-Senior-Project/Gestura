package com.example.gestura.dev

import com.google.firebase.Timestamp

data class ReviewContribution(
    val id: String = "",
    val label: String = "",
    val videoUrl: String = "",
    val uploaderEmail: String = "",
    val confidence: Double = 0.0,
    val createdAt: Timestamp? = null
)