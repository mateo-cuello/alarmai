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

    fun getMatchesForDate(context: Context, dateString: String): List<WorldCupMatch> {
        return try {
            val jsonString = context.assets.open("worldcup_2026.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
            parseMatches(jsonString, dateString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getTodayMatchesSummary(context: Context, dateString: String): String {
        val matches = getMatchesForDate(context, dateString)
        if (matches.isEmpty()) {
            return "No matches scheduled for today."
        }
        val sb = StringBuilder()
        sb.append("Today's FIFA World Cup 2026 Matches:\n")
        matches.forEach { match ->
            val groupInfo = if (match.group != null) " (${match.group})" else ""
            sb.append("- ${match.round}$groupInfo: ${match.team1} vs ${match.team2} at ${match.time} in ${match.ground}\n")
        }
        return sb.toString().trim()
    }
}
