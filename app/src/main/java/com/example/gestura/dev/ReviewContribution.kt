package com.example.gestura.dev

import com.google.firebase.Timestamp

data class ReviewContribution(
    val id: String = "",
    val word: String = "",
    val predictedLabel: String = "",
    val videoUrl: String = "",
    val userEmail: String = "",
    val confidence: Double = 0.0,
    val keypoints: List<Double> = emptyList(),
    val createdAt: Timestamp? = null
)