import re

with open('app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove WorldCup status message and data fetching
content = re.sub(r'\s*_statusMessage\.value = if \(isEs\) "Buscando partidos del Mundial\.\.\." else "Checking World Cup matches\.\.\."\n', '\n', content)
content = re.sub(r'\s*val sdf = java\.text\.SimpleDateFormat\("yyyy-MM-dd", java\.util\.Locale\.US\)\n', '\n', content)
content = re.sub(r'\s*val todayDateString = sdf\.format\(java\.util\.Date\(\)\)\n', '\n', content)
content = re.sub(r'\s*val worldCupRepository = com\.mateocuello\.alarmai\.data\.repository\.WorldCupRepository\(\)\n', '\n', content)
content = re.sub(r'\s*val worldCupData = worldCupRepository\.getTodayMatchesSummary\(getApplication\(\), todayDateString\)\n', '\n', content)

# Remove worldCupData parameter from startSession
content = re.sub(r'\s*worldCupData = worldCupData,?\n', '\n', content)

with open('app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
