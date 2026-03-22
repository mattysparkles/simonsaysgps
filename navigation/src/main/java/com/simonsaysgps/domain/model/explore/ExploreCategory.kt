package com.simonsaysgps.domain.model.explore

enum class ExploreCategory(
    val displayName: String,
    val helperText: String
) {
    DELICIOUS("Delicious", "Food and drink picks that favor quality, open hours, and short drives."),
    FUN("Fun", "Activities, games, entertainment, and playful detours worth the stop."),
    OPEN_NOW("Open Now", "Anything worthwhile that is open right now rises to the top."),
    NEVER_BEEN("I've Never Been", "Filters out places already seen in your visit history when possible."),
    QUIET("Quiet", "Prefers calm spaces such as parks, libraries, overlooks, and mellow cafes."),
    OUTDOORS("Outdoors", "Parks, trails, beaches, and other public outside attractions."),
    IMPORTANT("Important", "Museums, landmarks, civic places, and public-interest destinations."),
    CLOSE_TO_HOME("Close to Home", "Finds interesting options clustered near your saved home area."),
    ON_MY_WAY("On My Way", "Looks for bounded route detours near the active trip when available."),
    SPECIAL("Special", "Standout, scenic, iconic, unusual, or memorable recommendations."),
    NEW("New", "Boosts recently opened or newly trending places when the data exists."),
    I_CAN_SHOP("I Can Shop", "Retail strips, outlets, downtown shopping, and browse-worthy stops."),
    I_CAN_LEARN("I Can Learn", "Museums, libraries, visitor centers, and educational attractions."),
    GOOD_FOR_KIDS("Good for Kids", "Family-friendly experiences with kid-friendly boosts."),
    HAVING_A_SALE("Having a Sale", "Prefers promotions, openings, outlet-style savings, and visible deals.")
}
