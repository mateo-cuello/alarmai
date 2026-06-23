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

    @Test
    fun testFetchAllMatches_successfulNetworkCall() {
        val mockCall = org.mockito.Mockito.mock(okhttp3.Call::class.java)
        val mockResponse = org.mockito.Mockito.mock(okhttp3.Response::class.java)
        val mockResponseBody = org.mockito.Mockito.mock(okhttp3.ResponseBody::class.java)

        val apiJson = """
            {
              "Results": [
                {
                  "IdMatch": "1",
                  "Date": "2026-06-11T19:00:00Z",
                  "MatchDay": "1",
                  "Home": {
                    "TeamName": [
                      { "Description": "Mexico", "Locale": "en-GB" }
                    ]
                  },
                  "Away": {
                    "TeamName": [
                      { "Description": "South Africa", "Locale": "en-GB" }
                    ]
                  },
                  "GroupName": [
                    { "Description": "Group A", "Locale": "en-GB" }
                  ],
                  "StageName": [
                    { "Description": "Group Stage", "Locale": "en-GB" }
                  ],
                  "Stadium": {
                    "CityName": [
                      { "Description": "Mexico City", "Locale": "en-GB" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        org.mockito.Mockito.`when`(mockResponseBody.string()).thenReturn(apiJson)
        org.mockito.Mockito.`when`(mockResponse.isSuccessful).thenReturn(true)
        org.mockito.Mockito.`when`(mockResponse.body).thenReturn(mockResponseBody)
        org.mockito.Mockito.`when`(mockCall.execute()).thenReturn(mockResponse)

        val factory = okhttp3.Call.Factory { _ -> mockCall }
        val repo = WorldCupRepository(factory)
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)

        val matches = repo.getMatchesForDate(mockContext, "2026-06-11")
        assertEquals(1, matches.size)
        val firstMatch = matches[0]
        assertEquals("Matchday 1", firstMatch.round)
        assertEquals("2026-06-11", firstMatch.date)
        assertEquals("19:00 UTC", firstMatch.time)
        assertEquals("Mexico", firstMatch.team1)
        assertEquals("South Africa", firstMatch.team2)
        assertEquals("Group A", firstMatch.group)
        assertEquals("Mexico City", firstMatch.ground)
    }

    @Test
    fun testFetchAllMatches_networkFailure_fallsBackToAsset() {
        val mockCall = org.mockito.Mockito.mock(okhttp3.Call::class.java)
        
        org.mockito.Mockito.`when`(mockCall.execute()).thenThrow(java.io.IOException("Network error"))

        val factory = okhttp3.Call.Factory { _ -> mockCall }
        val repo = WorldCupRepository(factory)
        
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockAssetManager = org.mockito.Mockito.mock(android.content.res.AssetManager::class.java)
        org.mockito.Mockito.`when`(mockContext.assets).thenReturn(mockAssetManager)
        
        val assetJson = """
            {
              "name": "World Cup 2026",
              "matches": [
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
        
        org.mockito.Mockito.`when`(mockAssetManager.open("worldcup_2026.json"))
            .thenReturn(java.io.ByteArrayInputStream(assetJson.toByteArray()))

        val matches = repo.getMatchesForDate(mockContext, "2026-06-12")
        assertEquals(1, matches.size)
        val firstMatch = matches[0]
        assertEquals("Matchday 2", firstMatch.round)
        assertEquals("Canada", firstMatch.team1)
        assertEquals("Bosnia & Herzegovina", firstMatch.team2)
    }
}
