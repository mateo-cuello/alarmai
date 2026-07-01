import re

with open('app/src/main/java/com/mateocuello/alarmai/data/repository/GeminiAgentManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove WorldCupRepository property
content = re.sub(r'private val worldCupRepository: WorldCupRepository = WorldCupRepository\(\)\s*,?', '', content)

# 2. Remove getWorldCupContextText function
content = re.sub(r'private fun getWorldCupContextText\(\): String \{.*?return "\$instructions\\n\\nComplete FIFA World Cup 2026 Fixture Schedule \(JSON\):\\n\$fixture"\n    \}\n', '', content, flags=re.DOTALL)

# 3. Modify getSystemInstructionText
# Remove worldCupContext variable
content = re.sub(r'\s*val worldCupContext = getWorldCupContextText\(\)\n', '\n', content)
# Remove worldCupContext from template
content = re.sub(r'\s*\$worldCupContext\n', '\n', content)

# Remove World Cup references in Spanish prompt
content = re.sub(r'\s*- Copa Mundial FIFA 2026.*?\(junio y julio de 2026\)\.', '', content)
content = re.sub(r'\s*- Tienes herramientas para consultar el fixture del Mundial 2026.*?para partidos\.', '', content)
# Remove World Cup references in English prompt
content = re.sub(r'\s*- FIFA World Cup 2026: Currently ongoing \(June/July 2026\)\.', '', content)
content = re.sub(r'\s*- You have tools to query the 2026 World Cup fixture.*?for match information\.', '', content)

# Modify searchNews references in prompt to googleSearch
content = re.sub(r'- Tienes una herramienta para buscar noticias actuales \(searchNews\)\.', '- Tienes la herramienta de búsqueda de Google (googleSearch) para buscar noticias o información actual.', content)
content = re.sub(r'- Para noticias actualizadas o información sobre eventos actuales, usa la herramienta searchNews\.', '- Usa tu herramienta de búsqueda de Google cuando el usuario pregunte por información del mundo real.', content)
content = re.sub(r'- You have a tool to search current news headlines \(searchNews\)\.', '- You have the Google Search tool (googleSearch) to search for current news or information.', content)
content = re.sub(r'- For current news or headlines, use the searchNews tool\.', '- Use your Google Search tool when the user asks for real-world information.', content)

# Modify startSession signature and logic
content = re.sub(r',\s*worldCupData: String = "",', ',', content)
content = re.sub(r'\s*val wcInfo = if \(worldCupData.*?\} else " "\n', '\n', content, flags=re.DOTALL)
content = re.sub(r'\$\{wcInfo\}', ' ', content)
content = re.sub(r'\s*- Partidos de la Copa Mundial FIFA 2026 de hoy: \$worldCupData\n', '\n', content)
content = re.sub(r'\s*- Today\'s FIFA World Cup 2026 Matches: \$worldCupData\n', '\n', content)
content = re.sub(r' \(incluyendo los partidos del Mundial de hoy si los hay\)', '', content)
content = re.sub(r' \(including today\'s World Cup matches if any are scheduled\)', '', content)

# Modify the call
content = re.sub(r'worldCupData = worldCupData,\n\s*', '', content)

# Remove WorldCup and searchNews tools from buildRequestBody
content = re.sub(r'\s*// getWorldCupMatchesForDate.*?functionDeclarations\.put\(getWorldCupMatchesByTeam\)\n', '\n', content, flags=re.DOTALL)
content = re.sub(r'\s*// searchNews.*?functionDeclarations\.put\(searchNews\)\n', '\n', content, flags=re.DOTALL)

# Add Google Search tool
google_search = '''
        val googleSearchObject = JSONObject().apply {
            put("googleSearch", JSONObject())
        }
        toolsArray.put(googleSearchObject)
'''
content = content.replace('body.put("tools", toolsArray)', google_search + '\n        body.put("tools", toolsArray)')

# Remove WorldCup and searchNews function handlers
content = re.sub(r'\} else if \(name\.endsWith\("getWorldCupMatchesForDate"\).*?mapOf\("results" to result\)\)\)\n\s*\}', '}', content, flags=re.DOTALL)

# Remove them from demo responses (Spanish)
content = re.sub(r'\s*userInput\.contains\("mundial".*?\n\s*userInput\.contains\("calendario".*?\)\) -> \n.*?\n', '\n', content, flags=re.DOTALL)
# English
content = re.sub(r'\s*userInput\.contains\("world cup".*?\n\s*userInput\.contains\("calendar".*?\)\) -> \n.*?\n', '\n', content, flags=re.DOTALL)

with open('app/src/main/java/com/mateocuello/alarmai/data/repository/GeminiAgentManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
