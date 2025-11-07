/*
 * Copyright (c) 2025 Kraska s.r.o. <dev@kra.sk>
 * All Rights Reserved.
 */
package sk.kra.files.provider.kra.client

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import sk.kra.files.provider.common.UriAuthority
import sk.kra.files.util.takeIfNotEmpty

@Parcelize
data class KraAuthority(
    val host: String,
    val port: Int,
    val username: String
) : Parcelable {

    val hostWithPort: String
        get() = if (port != DEFAULT_PORT) "$host:$port" else host

    fun toUriAuthority(): UriAuthority {
        val userInfo = username.takeIfNotEmpty()
        val uriPort = port.takeIf { it != DEFAULT_PORT }
        return UriAuthority(userInfo, host, uriPort)
    }

    override fun toString(): String = toUriAuthority().toString()

    companion object {
        const val DEFAULT_PORT = 443
        const val DEFAULT_HOST = "api01.kra.sk"

        @Throws(IllegalArgumentException::class)
        fun fromString(authority: String): KraAuthority {
            val prefix = "kra://"
            require(authority.startsWith(prefix)) { "Authority must start with $prefix" }

            val withoutScheme = authority.substring(prefix.length)
            val atIndex = withoutScheme.indexOf('@')
            require(atIndex != -1) { "Authority must contain username@host" }

            val username = withoutScheme.substring(0, atIndex)
            require(username.isNotEmpty()) { "Username cannot be empty" }

            val hostPart = withoutScheme.substring(atIndex + 1)
            val host: String
            val port: Int
            if (':' in hostPart) {
                val parts = hostPart.split(':')
                require(parts.size == 2) { "Invalid host:port format" }
                host = parts[0]
                port = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid port number")
            } else {
                host = hostPart
                port = DEFAULT_PORT
            }

            require(host.isNotEmpty()) { "Host cannot be empty" }
            require(port in 1..65535) { "Port must be between 1 and 65535" }

            return KraAuthority(host, port, username)
        }
    }
}

@Parcelize
data class PasswordAuthentication(
    var password: String
) : Parcelable

fun PasswordAuthentication?.takeIfNotEmpty(): PasswordAuthentication? =
    this?.takeIf { it.password.isNotEmpty() }
