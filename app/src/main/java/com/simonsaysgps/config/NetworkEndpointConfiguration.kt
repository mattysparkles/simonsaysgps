package com.simonsaysgps.config

import com.simonsaysgps.BuildConfig
import java.net.URI

data class ResolvedNetworkEndpointConfiguration(
    val osrmBaseUrl: String,
    val nominatimBaseUrl: String,
    val graphHopperBaseUrl: String,
    val warnings: List<String>
)

object NetworkEndpointConfiguration {
    private const val SAFE_OSRM_BASE_URL = "https://router.project-osrm.org/"
    private const val SAFE_NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
    private const val SAFE_GRAPH_HOPPER_BASE_URL = "https://graphhopper.com/api/1/"

    fun resolve(
        osrmBaseUrl: String = BuildConfig.OSRM_BASE_URL,
        nominatimBaseUrl: String = BuildConfig.NOMINATIM_BASE_URL,
        graphHopperBaseUrl: String = BuildConfig.GRAPH_HOPPER_BASE_URL
    ): ResolvedNetworkEndpointConfiguration {
        val warnings = mutableListOf<String>()
        return ResolvedNetworkEndpointConfiguration(
            osrmBaseUrl = sanitize(
                configuredValue = osrmBaseUrl,
                safeFallback = SAFE_OSRM_BASE_URL,
                label = "OSRM_BASE_URL",
                warnings = warnings
            ),
            nominatimBaseUrl = sanitize(
                configuredValue = nominatimBaseUrl,
                safeFallback = SAFE_NOMINATIM_BASE_URL,
                label = "NOMINATIM_BASE_URL",
                warnings = warnings
            ),
            graphHopperBaseUrl = sanitize(
                configuredValue = graphHopperBaseUrl,
                safeFallback = SAFE_GRAPH_HOPPER_BASE_URL,
                label = "GRAPH_HOPPER_BASE_URL",
                warnings = warnings
            ),
            warnings = warnings
        )
    }

    private fun sanitize(
        configuredValue: String,
        safeFallback: String,
        label: String,
        warnings: MutableList<String>
    ): String {
        val sanitized = configuredValue.trim()
        val issue = validationIssue(sanitized)
        return when {
            sanitized.isBlank() -> {
                warnings += "$label is blank. Falling back to $safeFallback."
                safeFallback
            }

            issue != null -> {
                warnings += "$label is invalid ($issue). Falling back to $safeFallback."
                safeFallback
            }

            else -> sanitized.ensureTrailingSlash()
        }
    }

    private fun validationIssue(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: return "expected an absolute HTTPS URL"
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return "expected an absolute HTTPS URL"
        }
        return if (scheme == "https") null else "only HTTPS base URLs are accepted for release-ready network endpoints"
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
}
