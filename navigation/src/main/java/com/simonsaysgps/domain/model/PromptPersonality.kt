package com.simonsaysgps.domain.model

enum class PromptPersonality(
    val displayName: String,
    val description: String
) {
    CLASSIC_SIMON(
        displayName = "Classic Simon",
        description = "The original Simon voice with straightforward game-style calls."
    ),
    SNARKY_SIMON(
        displayName = "Snarky Simon",
        description = "A cheekier Simon who adds playful attitude to each prompt."
    ),
    POLITE_SIMON(
        displayName = "Polite Simon",
        description = "A courteous Simon who keeps directions friendly and clear."
    )
}
