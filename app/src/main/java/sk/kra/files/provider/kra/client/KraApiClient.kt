/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra.client

import android.util.Log
import com.google.gson.Gson
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class KraApiClient(
    private val authority: KraAuthority,
    private val authentication: PasswordAuthentication
) {
    private val gson = Gson()

    // Custom DNS resolver for emulator workaround
    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (hostname == "api01.kra.sk") {
                // Emulator DNS workaround: return IP address directly
                listOf(InetAddress.getByName("104.26.7.206"))
            } else {
                // Use system DNS for other hostnames
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .dns(customDns)  // Use custom DNS resolver
        .build()

    // Public access to httpClient for upload operations
    fun getHttpClient(): OkHttpClient = httpClient

    private val baseUrl: String
        get() = "https://${authority.host}:${authority.port}"

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var lastLoginTime: Long = 0

    private val sessionExpirationMs = 16L * 24 * 60 * 60 * 1000 // 16 days

    @Synchronized
    @Throws(IOException::class)
    fun updateAuthentication(newauth: PasswordAuthentication) {
        if (newauth.password.isNullOrEmpty() or authentication.password.equals(newauth.password)) {
            return
	}
        authentication.password = newauth.password
    }

    @Synchronized
    @Throws(IOException::class)
    fun ensureLoggedIn() {
        val currentTime = System.currentTimeMillis()
        if (sessionId != null && (currentTime - lastLoginTime) < sessionExpirationMs - 60000) {
            // Session still valid (with 1 minute buffer)
            return
        }
        login()
    }

    @Synchronized
    @Throws(IOException::class)
    private fun login() {
        val request = KraRequest(
            data = LoginData(
                username = authority.username,
                password = authentication.password
            )
        )

        val response = makeRequest<LoginData, Unit>("/api/user/login", request, requireSession = false)

        if (response.isError) {
            throw KraApiException(
                response.error ?: -1,
                response.msg ?: "Login failed"
            )
        }

        sessionId = response.sessionId ?: throw IOException("No session ID in login response")
        lastLoginTime = System.currentTimeMillis()
    }

    @Throws(IOException::class)
    fun getUserInfo(): UserInfo {
        ensureLoggedIn()
        val request = KraRequest<Unit>(sessionId = sessionId)
        val response = makeRequest<Unit, UserInfo>("/api/user/info", request)

        if (response.isError) {
            handleError(response)
        }

        return response.data ?: throw IOException("No data in user info response")
    }

    @Throws(IOException::class)
    fun getFileInfo(ident: String, password: String? = null): FileInfo {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = FileInfoRequest(ident, password)
        )
        val response = makeRequest<FileInfoRequest, FileInfo>("/api/file/info", request)

        if (response.isError) {
            handleError(response)
        }

        return response.data ?: throw IOException("No data in file info response")
    }

    @Throws(IOException::class)
    fun listFiles(
        parentIdent: String?,
        password: String? = null,
        sort: String = "name",
        desc: Boolean = false
    ): List<FileListItem> {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = FileListRequest(parentIdent, password, sort, desc)
        )
        val response = makeRequest<FileListRequest, List<FileListItem>>("/api/file/list", request)

        if (response.isError) {
            handleError(response)
        }

        return response.data ?: emptyList()
    }

    @Throws(IOException::class)
    fun getDownloadLink(ident: String, password: String? = null): DownloadInfo {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = DownloadRequest(ident, password)
        )
        val response = makeRequest<DownloadRequest, DownloadInfo>("/api/file/download", request)

        if (response.isError) {
            handleError(response)
        }

        return response.data ?: throw IOException("No data in download response")
    }

    @Throws(IOException::class)
    fun downloadFile(downloadUrl: String): InputStream {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Download failed: ${response.code} ${response.message}")
        }

        return response.body?.byteStream()
            ?: throw IOException("No response body in download")
    }

    @Throws(IOException::class)
    fun copyFile(
        ident: String,
        name: String? = null,
        parent: String? = null,
        shared: Boolean? = null,
        password: String? = null,
        newpassword: String? = null
    ): CopyFileInfo {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = CopyFileRequest(ident, name, parent, shared, password, newpassword)
        )
        val response = makeRequest<CopyFileRequest, CopyFileInfo>("/api/file/copy", request)

        if (response.isError) {
            handleError(response)
        }

        val copyFileInfo = response.data ?: throw IOException("No data in create file response")
        return copyFileInfo
    }

    @Throws(IOException::class)
    fun createFile(
        name: String,
        isFolder: Boolean,
        parent: String? = null,
        shared: Boolean? = null,
        password: String? = null
    ): CreateFileInfo {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = CreateFileRequest(name, isFolder, parent, shared, password)
        )
        val response = makeRequest<CreateFileRequest, CreateFileInfo>("/api/file/create", request)

        if (response.isError) {
            handleError(response)
        }

        val createInfo = response.data ?: throw IOException("No data in create file response")
        return createInfo
    }

    @Throws(IOException::class)
    fun deleteFile(ident: String) {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = DeleteRequest(ident)
        )
        val response = makeRequest<DeleteRequest, Unit>("/api/file/delete", request)

        if (response.isError) {
            handleError(response)
        }
    }

    @Throws(IOException::class)
    fun updateFile(
        ident: String,
        name: String? = null,
        parent: String? = null,
        shared: Boolean? = null,
        password: String? = null
    ) {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = UpdateFileRequest(ident, name, parent, shared, password)
        )
        val response = makeRequest<UpdateFileRequest, Unit>("/api/file/update", request)

        if (response.isError) {
            handleError(response)
        }
    }

    @Throws(IOException::class)
    fun getUploadLink(ident: String): UploadInfo {
        ensureLoggedIn()
        val request = KraRequest(
            sessionId = sessionId,
            data = UploadRequest(ident)
        )
        val response = makeRequest<UploadRequest, UploadInfo>("/api/file/upload", request)

        if (response.isError) {
            handleError(response)
        }

        val uploadInfo = response.data ?: throw IOException("No data in upload response")
        return uploadInfo
    }

    @Synchronized
    fun logout() {
        sessionId = null
        lastLoginTime = 0
    }

    @Throws(IOException::class)
    private inline fun <reified T, reified R> makeRequest(
        endpoint: String,
        requestData: KraRequest<T>,
        requireSession: Boolean = true
    ): KraResponse<R> {
        if (requireSession && requestData.sessionId == null) {
            throw IOException("Session ID required for $endpoint")
        }

        val json = gson.toJson(requestData)

        // KRA API requires Content-Type without charset
        val body = json.toRequestBody(null)

        val url = "$baseUrl$endpoint"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")  // Without charset
            .addHeader("User-Agent", "MaterialFiles-KRA/1.0")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body from $endpoint")

        if (!response.isSuccessful && response.code == 401) {
            throw KraApiException(401, "Unauthorized", null)
        }

        return try {
            // Parse the response structure first
            val jsonElement = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)

            val sessionId = jsonElement.get("session_id")?.asString
            val error = jsonElement.get("error")?.asInt
            val msg = jsonElement.get("msg")?.asString
            val dataElement = jsonElement.get("data")

            // Parse the data field with the correct type (if present)
            @Suppress("UNCHECKED_CAST")
            val data: R? = if (dataElement != null && !dataElement.isJsonNull) {
                // Use reified type parameter
                try {
                    gson.fromJson(dataElement, object : com.google.gson.reflect.TypeToken<R>() {}.type)
                } catch (e: Exception) {
                    // If parsing fails, might be Unit type
                    null
                }
            } else {
                null
            }

            KraResponse(
                success = null,
                error = error,
                msg = msg,
                sessionId = sessionId,
                data = data
            )
        } catch (e: Exception) {
            throw IOException("Failed to parse response from $endpoint: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    private fun <T> handleError(response: KraResponse<T>) {
        val errorCode = response.error ?: -1
        val message = response.msg ?: "Unknown error"

        val exception = KraApiException(errorCode, message)

        // Auto re-login on session errors
        if (exception.isSessionError) {
            sessionId = null
            lastLoginTime = 0
            throw IOException("Session expired, please retry", exception)
        }

        throw exception
    }

    companion object {
        private const val TAG = "KraApiClient"
    }
}
