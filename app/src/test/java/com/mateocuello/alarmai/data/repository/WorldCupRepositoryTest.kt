package com.mateocuello.alarmai.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldCupRepositoryTest {

    private val repository = WorldCupRepository()

    private val sampleJson = """
        {
          "name": "World Cup 2026",
          "matches": [
            {
              "round": "Matchday 1",
              "date": "2026-06-11",
              "time": "13:00 UTC-6",
              "team1": "Mexico",
              "team2": "South Africa",
              "group": "Group A",
              "ground": "Mexico City"
            },
            {
              "round": "Matchday 1",
              "date": "2026-06-11",
              "time": "20:00 UTC-6",
              "team1": "South Korea",
              "team2": "Czech Republic",
              "group": "Group A",
              "ground": "Guadalajara"
            },
            {
              "round": "Matchday 2",
              "date": "2026-06-12",
              "time": "15:00 UTC-4",
              "team1": "Canada",
              "team2": "Bosnia & Herzegovina",
              "group": "Group B",
              "ground": "Toronto"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testParseMatches_matchesFound() {
        val matches = repository.parseMatches(sampleJson, "2026-06-11")
        assertEquals(2, matches.size)
        
        val firstMatch = matches[0]
        assertEquals("Matchday 1", firstMatch.round)
        assertEquals("2026-06-11", firstMatch.date)
        assertEquals("13:00 UTC-6", firstMatch.time)
        assertEquals("Mexico", firstMatch.team1)
        assertEquals("South Africa", firstMatch.team2)
        assertEquals("Group A", firstMatch.group)
        assertEquals("Mexico City", firstMatch.ground)

        val secondMatch = matches[1]
        assertEquals("South Korea", secondMatch.team1)
        assertEquals("Czech Republic", secondMatch.team2)
    }

    @Test
    fun testParseMatches_noMatchesFound() {
        val matches = repository.parseMatches(sampleJson, "2026-06-20")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun testParseMatches_realFile() {
        val file = java.io.File("src/main/assets/worldcup_2026.json")
        assertTrue("worldcup_2026.json exists", file.exists())
        val jsonString = file.readText()
        val matches = repository.parseMatches(jsonString, "2026-06-12")
        
        // Assert matches for 2026-06-12 (Canada vs Bosnia and USA vs Paraguay)
        assertEquals(2, matches.size)
        
        val canadaMatch = matches.find { it.team1 == "Canada" }
        org.junit.Assert.assertNotNull(canadaMatch)
        assertEquals("Matchday 2", canadaMatch?.round)
        assertEquals("Bosnia & Herzegovina", canadaMatch?.team2)
        assertEquals("Toronto", canadaMatch?.ground)
        
        val usaMatch = matches.find { it.team1 == "USA" }
        org.junit.Assert.assertNotNull(usaMatch)
        assertEquals("Matchday 2", usaMatch?.round)
        assertEquals("Paraguay", usaMatch?.team2)
        assertEquals("Los Angeles (Inglewood)", usaMatch?.ground)
    }
}
