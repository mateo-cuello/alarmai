package com.alarmai.app.data.model

/**
 * Single source of truth for the Gemini models this app can talk to.
 *
 * Lives in `data.model` rather than on `GeminiAgentManager` so that `data.local`
 * (PreferencesManager) and the UI can both read it without depending on `data.repository`.
 *
 * [CHAIN] is ordered most-preferred first. `GeminiAgentManager` starts at the user's configured
 * model and walks *forward* through the remainder on quota/availability errors, so a model's
 * position determines what it can fall back to. Anything at the end of the list has no fallback.
 */
object GeminiModels {

    const val DEFAULT = "gemini-3.6-flash"

    val CHAIN = listOf(
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-3.0-flash",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite"
    )

    /** True when [model] is one this build knows how to fall back from. */
    fun isKnown(model: String): Boolean = model in CHAIN

    /** "gemini-3.6-flash" -> "Gemini 3.6 Flash", for display in the settings picker. */
    fun displayName(model: String): String =
        model.removePrefix("gemini-")
            .split("-")
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .let { "Gemini $it" }
}
