import re

with open('app/src/main/java/com/mateocuello/alarmai/service/PrefetchWorker.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove WorldCup imports and references
content = re.sub(r'import com.mateocuello.alarmai.data.repository.WorldCupRepository\n', '', content)
content = re.sub(r'\s*val worldCupRepository = WorldCupRepository\(\)\n', '\n', content)
content = re.sub(r'\s*val worldCupData = worldCupRepository\.getTodayMatchesSummary\(context, todayDateString\)\n', '\n', content)
content = re.sub(r'\s*worldCupData = worldCupData,\n', '\n', content)
content = re.sub(r'\s*- Partidos de la Copa Mundial FIFA 2026 de hoy: \$worldCupData\n', '\n', content)
content = re.sub(r'\s*- Today\'s FIFA World Cup 2026 Matches: \$worldCupData\n', '\n', content)
content = re.sub(r' \(incluyendo los partidos del Mundial de hoy si los hay\)', '', content)
content = re.sub(r' \(including today\'s World Cup matches if any are scheduled\)', '', content)

with open('app/src/main/java/com/mateocuello/alarmai/service/PrefetchWorker.kt', 'w', encoding='utf-8') as f:
    f.write(content)

with open('app/src/main/java/com/mateocuello/alarmai/data/repository/GeminiAgentManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the leftover World Cup prompts in GeminiAgentManager.kt
content = re.sub(r'\s*- Tienes herramientas para consultar el fixture del Mundial 2026 \(getWorldCupMatchesForDate y getWorldCupMatchesByTeam\)\.', '', content)
content = re.sub(r'\s*- DEBES usar getWorldCupMatchesForDate o getWorldCupMatchesByTeam para CUALQUIER pregunta sobre partidos del Mundial\. NUNCA adivines ni uses tu memoria interna para partidos\.', '', content)
content = re.sub(r'\s*- You have tools to query the 2026 World Cup fixture \(getWorldCupMatchesForDate and getWorldCupMatchesByTeam\)\.', '', content)
content = re.sub(r'\s*- You MUST use getWorldCupMatchesForDate or getWorldCupMatchesByTeam for ANY question about World Cup matches\. NEVER guess or use your internal memory for match information\.', '', content)

with open('app/src/main/java/com/mateocuello/alarmai/data/repository/GeminiAgentManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
