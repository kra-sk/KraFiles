/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra.client

import com.google.gson.annotations.SerializedName

// Base request wrapper
data class KraRequest<T>(
    @SerializedName("session_id")
    val sessionId: String? = null,
    @SerializedName("data")
    val data: T? = null
)

// Base response wrapper
data class KraResponse<T>(
    @SerializedName("success")
    val success: Int? = null,
    @SerializedName("error")
    val error: Int? = null,
    @SerializedName("msg")
    val msg: String? = null,
    @SerializedName("session_id")
    val sessionId: String? = null,
    @SerializedName("data")
    val data: T? = null
) {
    val isSuccess: Boolean
        get() = success != null && error == null

    val isError: Boolean
        get() = error != null
}

// Login request/response
data class LoginData(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String
)

// User info response
data class UserInfo(
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("objects")
    val objects: Int,
    @SerializedName("bytes")
    val bytes: Long,
    @SerializedName("object_quota")
    val objectQuota: Int,
    @SerializedName("bytes_quota")
    val bytesQuota: Long,
    @SerializedName("subscribed_until")
    val subscribedUntil: String?,
    @SerializedName("days_left")
    val daysLeft: Int,
    @SerializedName("mailing")
    val mailing: Boolean
)

// File info request
data class FileInfoRequest(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("password")
    val password: String? = null
)

// File info response
data class FileInfo(
    @SerializedName("name")
    val name: String,
    @SerializedName("folder")
    val folder: Boolean,
    @SerializedName("size")
    val size: Long? = null,
    @SerializedName("shared")
    val shared: Boolean? = null,
    @SerializedName("password")
    val password: Boolean? = null,
    @SerializedName("created")
    val created: Long? = null,
    @SerializedName("ident")
    val ident: String? = null,
    @SerializedName("archived")
    val archived: Boolean? = null
)

// File list request
data class FileListRequest(
    @SerializedName("ident")
    val ident: String?,
    @SerializedName("password")
    val password: String? = null,
    @SerializedName("sort")
    val sort: String = "name",
    @SerializedName("desc")
    val desc: Boolean = false
)

// File list response item
data class FileListItem(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("folder")
    val folder: Boolean,
    @SerializedName("size")
    val size: Long? = null,
    @SerializedName("created")
    val created: Long? = null,
    @SerializedName("shared")
    val shared: Boolean? = null,
    @SerializedName("password")
    val password: Boolean? = null
)

// Download request
data class DownloadRequest(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("password")
    val password: String? = null
)

// Download response
data class DownloadInfo(
    @SerializedName("link")
    val link: String,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("expires")
    val expires: String
)

// Create file request
data class CreateFileRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("folder")
    val folder: Boolean,
    @SerializedName("parent")
    val parent: String? = null,
    @SerializedName("shared")
    val shared: Boolean? = null,
    @SerializedName("password")
    val password: String? = null
)

// Create file response
data class CreateFileInfo(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("link")
    val link: String? = null
)

// Copy file request
data class CopyFileRequest(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("parent")
    val parent: String? = null,
    @SerializedName("shared")
    val shared: Boolean? = null,
    @SerializedName("password")
    val password: String? = null,
    @SerializedName("newpassword")
    val newpassword: String? = null
)

// Copy file response
data class CopyFileInfo(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("shared")
    val shared: Boolean,
    @SerializedName("password")
    val password: Boolean
)

// Delete request
data class DeleteRequest(
    @SerializedName("ident")
    val ident: String
)

// Upload request
data class UploadRequest(
    @SerializedName("ident")
    val ident: String
)

// Upload response
data class UploadInfo(
    @SerializedName("link")
    val link: String
)

// Update file request
data class UpdateFileRequest(
    @SerializedName("ident")
    val ident: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("parent")
    val parent: String? = null,
    @SerializedName("shared")
    val shared: Boolean? = null,
    @SerializedName("password")
    val password: String? = null
)

// Exception for KRA API errors
class KraApiException(
    val errorCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception("KRA API Error $errorCode: $message", cause) {

    val isAuthenticationError: Boolean
        get() = errorCode == 401 || errorCode == 1100 || errorCode == 1103 || errorCode == 1104

    val isSessionError: Boolean
        get() = errorCode == 1203 || errorCode == 1204

    val isSubscriptionRequired: Boolean
        get() = errorCode == 1108

    val isRateLimited: Boolean
        get() = errorCode == 1903
}
