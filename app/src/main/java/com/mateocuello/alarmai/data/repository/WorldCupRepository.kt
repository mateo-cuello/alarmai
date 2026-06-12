package com.mateocuello.alarmai.data.repository

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class WorldCupMatch(
    val round: String,
    val date: String,
    val time: String,
    val team1: String,
    val team2: String,
    val group: String?,
    val ground: String
)

class WorldCupRepository {

    fun parseMatches(jsonString: String, dateString: String): List<WorldCupMatch> {
        val matches = mutableListOf<WorldCupMatch>()
        try {
            // Find all match objects enclosed in { ... }
            val matchRegex = Regex("""\{([^}]+)}""")
            val matchesFound = matchRegex.findAll(jsonString)
            
            for (matchResult in matchesFound) {
                val matchBody = matchResult.groupValues[1]
                
                // Parse date
                val date = parseJsonField(matchBody, "date") ?: continue
                if (date != dateString) continue
                
                val round = parseJsonField(matchBody, "round") ?: ""
                val time = parseJsonField(matchBody, "time") ?: ""
                val team1 = parseJsonField(matchBody, "team1") ?: ""
                val team2 = parseJsonField(matchBody, "team2") ?: ""
                val group = parseJsonField(matchBody, "group")
                val ground = parseJsonField(matchBody, "ground") ?: ""
                
                matches.add(
                    WorldCupMatch(
                        round = round,
                        date = date,
                        time = time,
                        team1 = team1,
                        team2 = team2,
                        group = group,
                        ground = ground
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return matches
    }

    private fun parseJsonField(matchBody: String, fieldName: String): String? {
        // Find "fieldName": "value"
        val pattern = Regex(""""$fieldName"\s*:\s*"([^"]*)"""")
        val match = pattern.find(matchBody)
        if (match != null) {
            return match.groupValues[1]
        }
        // Fallback for non-string fields or unquoted values (e.g., numbers, null)
        val numPattern = Regex(""""$fieldName"\s*:\s*([^,\s]*)""")
        val numMatch = numPattern.find(matchBody)
        if (numMatch != null) {
            val value = numMatch.groupValues[1].trim()
            if (value != "null") return value
        }
        return null
    }

    fun normalizeDateString(dateStr: String): String {
        val trimmed = dateStr.trim().lowercase()
        val now = java.util.Date()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        
        return when {
            trimmed == "today" || trimmed == "hoy" -> sdf.format(now)
            trimmed == "tomorrow" || trimmed == "mañana" -> {
                val cal = java.util.Calendar.getInstance()
                cal.time = now
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                sdf.format(cal.time)
            }
            trimmed == "yesterday" || trimmed == "ayer" -> {
                val cal = java.util.Calendar.getInstance()
                cal.time = now
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                sdf.format(cal.time)
            }
            else -> {
                val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
                val match = dateRegex.find(trimmed)
                if (match != null) {
                    match.value
                } else {
                    dateStr
                }
            }
        }
    }

    fun getMatchesForDate(context: Context, dateString: String): List<WorldCupMatch> {
        val normalizedDate = normalizeDateString(dateString)
        return try {
            val jsonString = context.assets.open("worldcup_2026.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
            parseMatches(jsonString, normalizedDate)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getTodayMatchesSummary(context: Context, dateString: String): String {
        val normalizedDate = normalizeDateString(dateString)
        val matches = getMatchesForDate(context, normalizedDate)
        if (matches.isEmpty()) {
            return "No matches scheduled for $normalizedDate."
        }
        val sb = StringBuilder()
        sb.append("FIFA World Cup 2026 Matches for $normalizedDate:\n")
        matches.forEach { match ->
            val groupInfo = if (match.group != null) " (${match.group})" else ""
            sb.append("- ${match.round}$groupInfo: ${match.team1} vs ${match.team2} at ${match.time} in ${match.ground}\n")
        }
        return sb.toString().trim()
    }

    /**
     * Searches all World Cup matches for a specific team.
     * Supports common name aliases (e.g., "Estados Unidos" -> "USA").
     */
    fun getMatchesByTeam(context: Context, teamName: String): String {
        val normalizedTeam = normalizeTeamName(teamName)
        
        return try {
            val jsonString = context.assets.open("worldcup_2026.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
            
            val allMatches = parseAllMatches(jsonString)
            val teamMatches = allMatches.filter { match ->
                match.team1.contains(normalizedTeam, ignoreCase = true) ||
                match.team2.contains(normalizedTeam, ignoreCase = true)
            }
            
            if (teamMatches.isEmpty()) {
                "No matches found for team '$teamName' in the 2026 World Cup fixture."
            } else {
                val sb = StringBuilder()
                sb.append("FIFA World Cup 2026 matches for $teamName:\n")
                teamMatches.forEach { match ->
                    val groupInfo = if (match.group != null) " (${match.group})" else ""
                    sb.append("- ${match.date}: ${match.round}$groupInfo: ${match.team1} vs ${match.team2} at ${match.time} in ${match.ground}\n")
                }
                sb.toString().trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error searching for team '$teamName': ${e.localizedMessage}"
        }
    }

    /**
     * Parses ALL matches from the JSON (not filtered by date).
     */
    private fun parseAllMatches(jsonString: String): List<WorldCupMatch> {
        val matches = mutableListOf<WorldCupMatch>()
        try {
            val matchRegex = Regex("""\{([^}]+)}""")
            val matchesFound = matchRegex.findAll(jsonString)
            
            for (matchResult in matchesFound) {
                val matchBody = matchResult.groupValues[1]
                
                val date = parseJsonField(matchBody, "date") ?: continue
                val round = parseJsonField(matchBody, "round") ?: ""
                val time = parseJsonField(matchBody, "time") ?: ""
                val team1 = parseJsonField(matchBody, "team1") ?: ""
                val team2 = parseJsonField(matchBody, "team2") ?: ""
                val group = parseJsonField(matchBody, "group")
                val ground = parseJsonField(matchBody, "ground") ?: ""
                
                matches.add(
                    WorldCupMatch(
                        round = round,
                        date = date,
                        time = time,
                        team1 = team1,
                        team2 = team2,
                        group = group,
                        ground = ground
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return matches
    }

    /**
     * Normalizes team names to match the fixture data.
     * Handles common aliases and Spanish names.
     */
    private fun normalizeTeamName(name: String): String {
        val trimmed = name.trim()
        val aliases = mapOf(
            "estados unidos" to "USA",
            "eeuu" to "USA",
            "ee.uu." to "USA",
            "united states" to "USA",
            "america" to "USA",
            "eeuu" to "USA",
            "brasil" to "Brazil",
            "alemania" to "Germany",
            "francia" to "France",
            "españa" to "Spain",
            "inglaterra" to "England",
            "paises bajos" to "Netherlands",
            "países bajos" to "Netherlands",
            "holanda" to "Netherlands",
            "holland" to "Netherlands",
            "corea del sur" to "South Korea",
            "corea" to "South Korea",
            "south korea" to "South Korea",
            "korea" to "South Korea",
            "suiza" to "Switzerland",
            "marruecos" to "Morocco",
            "belgica" to "Belgium",
            "bélgica" to "Belgium",
            "suecia" to "Sweden",
            "tunez" to "Tunisia",
            "túnez" to "Tunisia",
            "croacia" to "Croatia",
            "japon" to "Japan",
            "japón" to "Japan",
            "turquia" to "Turkey",
            "turquía" to "Turkey",
            "noruega" to "Norway",
            "sudafrica" to "South Africa",
            "sudáfrica" to "South Africa",
            "republica checa" to "Czech Republic",
            "república checa" to "Czech Republic",
            "costa de marfil" to "Ivory Coast",
            "haiti" to "Haiti",
            "haití" to "Haiti",
            "escocia" to "Scotland",
            "canada" to "Canada",
            "canadá" to "Canada",
            "nueva zelanda" to "New Zealand",
            "nueva zelandia" to "New Zealand",
            "cabo verde" to "Cape Verde",
            "arabia saudita" to "Saudi Arabia",
            "argeliaa" to "Algeria",
            "argelia" to "Algeria",
            "jordania" to "Jordan",
            "austria" to "Austria",
            "irak" to "Iraq",
            "irán" to "Iran",
            "iran" to "Iran",
            "ghana" to "Ghana",
            "panamá" to "Panama",
            "panama" to "Panama",
            "senegal" to "Senegal",
            "rd congo" to "DR Congo",
            "rep. dem. congo" to "DR Congo",
            "uzbekistan" to "Uzbekistan",
            "uzbekistán" to "Uzbekistan",
            "egipto" to "Egypt",
            "curazao" to "Curaçao",
            "curacao" to "Curaçao",
            "bosnia" to "Bosnia & Herzegovina",
            "bosnia y herzegovina" to "Bosnia & Herzegovina",
            "bosnia and herzegovina" to "Bosnia & Herzegovina",
            "qatar" to "Qatar",
            "catar" to "Qatar",
            "ecuador" to "Ecuador",
            "colombia" to "Colombia",
            "portugal" to "Portugal",
            "uruguay" to "Uruguay",
            "mexico" to "Mexico",
            "méxico" to "Mexico",
            "paraguay" to "Paraguay",
            "argentina" to "Argentina"
        )
        return aliases[trimmed.lowercase()] ?: trimmed
    }
}

