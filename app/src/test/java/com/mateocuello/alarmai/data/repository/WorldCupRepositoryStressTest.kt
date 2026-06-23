package com.mateocuello.alarmai.data.repository

import android.content.Context
import android.content.res.AssetManager
import okhttp3.Call
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.io.IOException

class WorldCupRepositoryStressTest {

    private lateinit var mockContext: Context
    private lateinit var mockAssetManager: AssetManager

    private val fallbackAssetJson = """
        {
          "name": "World Cup 2026 Fallback",
          "matches": [
            {
              "round": "Matchday 1",
              "date": "2026-06-11",
              "time": "13:00 UTC-6",
              "team1": "Mexico",
              "team2": "South Africa",
              "group": "Group A",
              "ground": "Mexico City"
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAssetManager = mock(AssetManager::class.java)
        `when`(mockContext.assets).thenReturn(mockAssetManager)
        `when`(mockAssetManager.open("worldcup_2026.json"))
            .thenReturn(ByteArrayInputStream(fallbackAssetJson.toByteArray()))
    }

    private fun createMockClient(
        isSuccessful: Boolean,
        responseCode: Int = 200,
        responseBodyContent: String? = null,
        shouldThrow: Boolean = false
    ): okhttp3.Call.Factory {
        val mockCall = mock(Call::class.java)
        
        if (shouldThrow) {
            `when`(mockCall.execute()).thenThrow(IOException("Simulated Network Timeout"))
        } else {
            val mockResponse = mock(Response::class.java)
            `when`(mockResponse.isSuccessful).thenReturn(isSuccessful)
            `when`(mockResponse.code).thenReturn(responseCode)
            
            if (responseBodyContent != null) {
                val mockBody = mock(ResponseBody::class.java)
                `when`(mockBody.string()).thenReturn(responseBodyContent)
                `when`(mockResponse.body).thenReturn(mockBody)
            } else {
                `when`(mockResponse.body).thenReturn(null)
            }
            
            `when`(mockCall.execute()).thenReturn(mockResponse)
        }
        
        return okhttp3.Call.Factory { mockCall }
    }

    @Test
    fun testEmptyResults_fallsBackToAsset() {
        // API returns {"Results": []} which has no match data
        val apiJson = "{\"Results\": []}"
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        // Since API parse returns empty list, it must fall back to asset
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testMissingResultsKey_fallsBackToAsset() {
        // API returns {} (no Results key)
        val apiJson = "{}"
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testHTTP_404_fallsBackToAsset() {
        // API returns 404 Not Found
        val factory = createMockClient(isSuccessful = false, responseCode = 404)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testHTTP_500_fallsBackToAsset() {
        // API returns 500 Internal Server Error
        val factory = createMockClient(isSuccessful = false, responseCode = 500)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testNetworkTimeout_fallsBackToAsset() {
        // Client throws IOException during request execution
        val factory = createMockClient(isSuccessful = false, shouldThrow = true)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testInvalidJsonSyntax_fallsBackToAsset() {
        // API returns malformed JSON
        val apiJson = "{invalid_json"
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testNullKeysAndDefaultValuesInAPI() {
        // API returns null or empty values for match days, stadium names, team names
        val apiJson = """
            {
              "Results": [
                {
                  "IdMatch": "2",
                  "Date": "2026-06-12T15:00:00Z",
                  "MatchDay": null,
                  "Home": null,
                  "Away": {
                    "TeamName": []
                  },
                  "GroupName": null,
                  "StageName": [
                    { "Description": "Final", "Locale": "en-GB" }
                  ],
                  "Stadium": {
                    "CityName": null,
                    "City": [
                      { "Description": "Dallas", "Locale": "en-GB" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        val match = matches[0]
        assertEquals("Final", match.round)
        assertEquals("2026-06-12", match.date)
        assertEquals("15:00 UTC", match.time)
        assertEquals("", match.team1) // Home was null
        assertEquals("", match.team2) // Away had empty TeamName
        assertEquals(null, match.group) // GroupName was null
        assertEquals("Dallas", match.ground) // CityName was null, fell back to City
    }

    @Test
    fun testLocaleFallbacks() {
        // Translation arrays don't contain English (en) locales
        val apiJson = """
            {
              "Results": [
                {
                  "IdMatch": "3",
                  "Date": "2026-06-13T18:00:00Z",
                  "MatchDay": "3",
                  "Home": {
                    "TeamName": [
                      { "Description": "Brésil", "Locale": "fr-FR" },
                      { "Description": "Brasil", "Locale": "pt-BR" }
                    ]
                  },
                  "Away": {
                    "TeamName": [
                      { "Description": "Allemagne", "Locale": "fr-FR" }
                    ]
                  },
                  "GroupName": [
                    { "Description": "Groupe A", "Locale": "fr-FR" }
                  ],
                  "Stadium": {
                    "CityName": [
                      { "Description": "Rio", "Locale": "pt-BR" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        assertEquals(1, matches.size)
        val match = matches[0]
        // Since there is no "en" locale, it should fall back to the first element in each array
        assertEquals("Brésil", match.team1)
        assertEquals("Allemagne", match.team2)
        assertEquals("Groupe A", match.group)
        assertEquals("Rio", match.ground)
    }

    @Test
    fun testCorruptMatchesInResultsArray() {
        // A results array with some valid elements and one corrupt element that throws
        val apiJson = """
            {
              "Results": [
                {
                  "IdMatch": "1",
                  "Date": "2026-06-11T12:00:00Z",
                  "MatchDay": "1",
                  "Home": { "TeamName": [ { "Description": "Mexico", "Locale": "en-GB" } ] },
                  "Away": { "TeamName": [ { "Description": "South Africa", "Locale": "en-GB" } ] },
                  "GroupName": null,
                  "Stadium": null
                },
                "this_is_a_corrupt_string_element_not_an_object",
                {
                  "IdMatch": "2",
                  "Date": "2026-06-12T15:00:00Z",
                  "MatchDay": "2",
                  "Home": { "TeamName": [ { "Description": "USA", "Locale": "en-GB" } ] },
                  "Away": { "TeamName": [ { "Description": "Paraguay", "Locale": "en-GB" } ] },
                  "GroupName": null,
                  "Stadium": null
                }
              ]
            }
        """.trimIndent()
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        
        // Assert that the parsing didn't completely crash the application, but it returned the partially parsed matches (1 match parsed before exception)
        // Wait, is that the current implementation behavior? Let's verify!
        // The first match is parsed successfully. The second is corrupt (JSONObject(i) throws exception).
        // Since the exception is caught on parseFifaMatchesJson, it returns the partially parsed list.
        // It has 1 element, which is not empty, so fetchAllMatches returns it and doesn't fall back.
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }

    @Test
    fun testNestedArrayVariation_fallsBackToAsset() {
        // TeamName is a nested array of arrays (array of arrays of objects)
        val apiJson = """
            {
              "Results": [
                {
                  "IdMatch": "1",
                  "Date": "2026-06-11T12:00:00Z",
                  "MatchDay": "1",
                  "Home": {
                    "TeamName": [
                      [
                        { "Description": "Mexico", "Locale": "en-GB" }
                      ]
                    ]
                  },
                  "Away": { "TeamName": [ { "Description": "South Africa", "Locale": "en-GB" } ] },
                  "GroupName": null,
                  "Stadium": null
                }
              ]
            }
        """.trimIndent()
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        // Since TeamName has a nested array, parsing throws JSONException, causing empty list, falling back to asset.
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
        assertEquals("Matchday 1", matches[0].round)
    }

    @Test
    fun testCorruptFirstMatch_fallsBackToAsset() {
        // The first match is a corrupt element (string instead of JSONObject)
        val apiJson = """
            {
              "Results": [
                "corrupt_element_first",
                {
                  "IdMatch": "2",
                  "Date": "2026-06-12T15:00:00Z",
                  "MatchDay": "2",
                  "Home": { "TeamName": [ { "Description": "USA", "Locale": "en-GB" } ] },
                  "Away": { "TeamName": [ { "Description": "Paraguay", "Locale": "en-GB" } ] },
                  "GroupName": null,
                  "Stadium": null
                }
              ]
            }
        """.trimIndent()
        val factory = createMockClient(isSuccessful = true, responseBodyContent = apiJson)
        val repo = WorldCupRepository(factory)

        val matches = repo.fetchAllMatches(mockContext)
        // The first element is corrupt, throwing exception immediately and resulting in empty list -> fallback to asset
        assertEquals(1, matches.size)
        assertEquals("Mexico", matches[0].team1)
    }
}
