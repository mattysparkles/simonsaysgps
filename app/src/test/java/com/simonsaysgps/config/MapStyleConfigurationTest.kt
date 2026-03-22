package com.simonsaysgps.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapStyleConfigurationTest {
    @Test
    fun `uses fallback when configured map style URL is blank`() {
        val resolved = MapStyleConfiguration.resolve(
            configuredStyleUrl = "   ",
            fallbackStyleUrl = "https://demotiles.maplibre.org/style.json",
            allowHttp = false
        )

        assertThat(resolved.styleUrl).isEqualTo("https://demotiles.maplibre.org/style.json")
        assertThat(resolved.usesFallback).isTrue()
        assertThat(resolved.warningMessage).contains("MAP_STYLE_URL is blank")
    }

    @Test
    fun `rejects insecure non-local HTTP style URLs by default`() {
        val resolved = MapStyleConfiguration.resolve(
            configuredStyleUrl = "http://tiles.example.com/styles/prod/style.json",
            fallbackStyleUrl = "https://demotiles.maplibre.org/style.json",
            allowHttp = false
        )

        assertThat(resolved.styleUrl).isEqualTo("https://demotiles.maplibre.org/style.json")
        assertThat(resolved.usesFallback).isTrue()
        assertThat(resolved.warningMessage).contains("ALLOW_HTTP_MAP_STYLE_URL=true")
    }

    @Test
    fun `allows localhost HTTP style URLs for development`() {
        val resolved = MapStyleConfiguration.resolve(
            configuredStyleUrl = "http://10.0.2.2:8080/styles/dev/style.json",
            fallbackStyleUrl = "https://demotiles.maplibre.org/style.json",
            allowHttp = false
        )

        assertThat(resolved.styleUrl).isEqualTo("http://10.0.2.2:8080/styles/dev/style.json")
        assertThat(resolved.usesFallback).isFalse()
        assertThat(resolved.warningMessage).isNull()
    }

    @Test
    fun `accepts secure production HTTPS style URLs`() {
        val resolved = MapStyleConfiguration.resolve(
            configuredStyleUrl = "https://maps.example.com/styles/prod/style.json",
            fallbackStyleUrl = "https://demotiles.maplibre.org/style.json",
            allowHttp = false
        )

        assertThat(resolved.styleUrl).isEqualTo("https://maps.example.com/styles/prod/style.json")
        assertThat(resolved.usesFallback).isFalse()
        assertThat(resolved.warningMessage).isNull()
    }
}
