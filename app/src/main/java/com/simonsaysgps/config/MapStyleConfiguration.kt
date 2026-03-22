package com.simonsaysgps.config

import com.simonsaysgps.BuildConfig
import java.net.URI

data class ResolvedMapStyleConfiguration(
    val styleUrl: String,
    val usesFallback: Boolean,
    val warningMessage: String?
)

object MapStyleConfiguration {
    fun resolve(
        configuredStyleUrl: String = BuildConfig.MAP_STYLE_URL,
        fallbackStyleUrl: String = BuildConfig.MAP_STYLE_FALLBACK_URL,
        allowHttp: Boolean = BuildConfig.ALLOW_HTTP_MAP_STYLE_URL
    ): ResolvedMapStyleConfiguration {
        val sanitizedFallback = fallbackStyleUrl.trim()
        require(isSupportedStyleUrl(sanitizedFallback, allowHttp = true)) {
            "MAP_STYLE_FALLBACK_URL must be a valid absolute HTTP(S) URL."
        }

        val sanitizedConfigured = configuredStyleUrl.trim()
        if (sanitizedConfigured.isBlank()) {
            return ResolvedMapStyleConfiguration(
                styleUrl = sanitizedFallback,
                usesFallback = true,
                warningMessage = "MAP_STYLE_URL is blank. Falling back to the development style URL."
            )
        }

        val issue = validationIssue(sanitizedConfigured, allowHttp)
        return if (issue == null) {
            ResolvedMapStyleConfiguration(
                styleUrl = sanitizedConfigured,
                usesFallback = false,
                warningMessage = null
            )
        } else {
            ResolvedMapStyleConfiguration(
                styleUrl = sanitizedFallback,
                usesFallback = true,
                warningMessage = "Invalid MAP_STYLE_URL: $issue Falling back to the development style URL."
            )
        }
    }

    private fun validationIssue(styleUrl: String, allowHttp: Boolean): String? {
        val uri = runCatching { URI(styleUrl) }.getOrNull()
            ?: return "expected an absolute URL with an HTTP or HTTPS scheme"
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return "expected an absolute URL with an HTTP or HTTPS scheme"
        }

        if (scheme != "https" && scheme != "http") {
            return "only HTTP(S) MapLibre style URLs are supported"
        }

        if (scheme == "http" && !allowHttp && !isLocalDevelopmentHost(host)) {
            return "HTTP is only allowed for localhost-style development hosts unless ALLOW_HTTP_MAP_STYLE_URL=true"
        }

        return null
    }

    private fun isSupportedStyleUrl(styleUrl: String, allowHttp: Boolean): Boolean =
        validationIssue(styleUrl = styleUrl, allowHttp = allowHttp) == null

    private fun isLocalDevelopmentHost(host: String): Boolean = host in setOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2"
    )
}
