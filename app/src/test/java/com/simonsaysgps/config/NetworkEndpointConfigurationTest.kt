package com.simonsaysgps.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkEndpointConfigurationTest {
    @Test
    fun `falls back to safe HTTPS defaults when base URLs are blank or invalid`() {
        val resolved = NetworkEndpointConfiguration.resolve(
            osrmBaseUrl = "   ",
            nominatimBaseUrl = "http://example.com/search",
            graphHopperBaseUrl = "not-a-url"
        )

        assertThat(resolved.osrmBaseUrl).isEqualTo("https://router.project-osrm.org/")
        assertThat(resolved.nominatimBaseUrl).isEqualTo("https://nominatim.openstreetmap.org/")
        assertThat(resolved.graphHopperBaseUrl).isEqualTo("https://graphhopper.com/api/1/")
        assertThat(resolved.warnings).hasSize(3)
    }

    @Test
    fun `preserves valid HTTPS base URLs and normalizes trailing slashes`() {
        val resolved = NetworkEndpointConfiguration.resolve(
            osrmBaseUrl = "https://router.example.com",
            nominatimBaseUrl = "https://search.example.com/api/",
            graphHopperBaseUrl = "https://gh.example.com/api"
        )

        assertThat(resolved.osrmBaseUrl).isEqualTo("https://router.example.com/")
        assertThat(resolved.nominatimBaseUrl).isEqualTo("https://search.example.com/api/")
        assertThat(resolved.graphHopperBaseUrl).isEqualTo("https://gh.example.com/api/")
        assertThat(resolved.warnings).isEmpty()
    }
}
