package com.example.gestura.data

class AvatarRepository(
    private val api: AvatarApi = Network.api
) {
    suspend fun generate(text: String): String {
        val resp = api.generate(AvatarRequest(text = text))
        return resp.videoUrl
    }
}