# Environment Configuration Plan

## Objective
Set up the project environment configurations so that API keys are loaded securely.

## Step-by-Step Execution Plan

### Step 1: Create `.env` file
Create `.env` file at the project root `c:\Users\usuario\alarmai\.env` with placeholder values:
```env
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
NEWS_API_KEY=YOUR_NEWS_API_KEY_HERE
```
Verify the file is created at the correct location.

### Step 2: Modify `.gitignore`
Append `.env` to `c:\Users\usuario\alarmai\.gitignore`.
Verify `.gitignore` contains `.env`.

### Step 3: Modify `app/build.gradle.kts`
Modify `app/build.gradle.kts` to load variables from `.env` and add buildConfig fields inside the `android` block.
Verify `app/build.gradle.kts` changes.

### Step 4: Modify `PreferencesManager.kt`
- Import `com.mateocuello.alarmai.BuildConfig`.
- Update `getGeminiKey()` to fall back to `BuildConfig.GEMINI_API_KEY`.
- Update `getNewsKey()` to fall back to `BuildConfig.NEWS_API_KEY`.
Verify the changes manually.

### Step 5: Run Clean Build
Execute `.\gradlew.bat clean assembleDebug` to compile the app and generate the `BuildConfig` fields from the `.env` file.
Verify that compilation completes without errors.

### Step 6: Run Unit Tests
Execute `.\gradlew.bat test` to verify all unit tests pass, including checking if we need to adapt or add any unit tests.
Verify that all unit tests pass.

### Step 7: Run Git Verification Check
Execute `git status` or `git check-ignore .env` to verify `.env` is ignored by Git.
Verify the output confirms `.env` is ignored.
