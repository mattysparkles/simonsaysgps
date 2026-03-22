package com.simonsaysgps.domain.model

data class PlaceResult(
    val id: String,
    val name: String,
    val fullAddress: String,
    val coordinate: Coordinate
)
