package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val message: String,
    val user: User
)

@Serializable
data class User(
    val id: Int,
    val username: String,
    val email: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val inviteCode: String
)
