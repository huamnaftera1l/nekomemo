package com.example.nekomemo

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import com.google.gson.JsonObject
import retrofit2.http.GET

interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @GET("v1/models")                           // 通用探活端点
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<Unit>                           // 只关心 HTTP 码
}

data class ChatCompletionRequest(
    val model: String = "gpt-4o",
    val messages: List<Message>,
    val max_tokens: Int = 800,
    val temperature: Double = 0.7
)

data class Message(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<Choice>,
    val usage: Usage? = null
)

data class Choice(
    val message: Message
)

data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)