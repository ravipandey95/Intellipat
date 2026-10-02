# Course Learning App

Kotlin · Jetpack Compose · Hilt · Retrofit · Room · Coroutines/Flow

This link contains the Demo Video and a Screenshot of the passing test.
[Google Drive](https://drive.google.com/drive/folders/1mERRazGKfp0PVbnyn3Y8mxnG6zXXi9-B?usp=sharing)
## 1. Architecture
MVVM with a Repository layer and one-way data flow:
`Compose UI → ViewModel (StateFlow<UiState>) → Repository → Retrofit API + Room DAO`.

- Screens are stateless and previewable, ViewModels own UI state, repositories own data rules (e.g. only an HTTP 200 updates the database).
- Hilt injects interfaces, so the fake API and a real one swap with a single binding, and tests can replace dependencies.
- Room is the single source of truth. This makes offline support and consistency between screens (home ↔ details progress) automatic.

## 2. Offline Support
- Room stores the session, courses and lessons. The UI only observes the database; the network only refreshes it.
- A refresh replaces data only on HTTP 200. On failure, saved data stays visible with a "network error, showing saved courses" banner. If nothing is saved, a full-screen error with Retry is shown.
- Lesson check/uncheck is local. Course progress is recalculated in SQL from the lessons, so home and details update instantly, and server refreshes never overwrite the user's choices.
- The login session is persisted (auto-login). A salted PBKDF2 hash allows sign-in when the API is unreachable. Logout clears cached data.

## 3. Security
Store the tokens in **SharedPreferences, with the values encrypted** by a hardware-backed **Android Keystore** key (e.g. Tink AEAD), because plain SharedPreferences can be read on rooted devices or from backups. An OkHttp interceptor reads the token from there and adds the `Authorization` header to every Retrofit call (and refreshes it on a 401). Use a short-lived access token plus a refresh token, HTTPS only (optionally certificate pinning), exclude the file from backups, and wipe it on logout. The demo keeps the token in Room for simplicity. Raw passwords are never stored.

## 4. Scale (1M users, hundreds of courses)
1. **Pagination:** Paging 3 with a RemoteMediator instead of loading every course.
2. **Incremental sync:** ETag/`updatedAt` or delta endpoints with upserts, not replacing whole tables.
3. **Progress sync:** send lesson progress to the server through a WorkManager outbox with retries and conflict resolution (it is device-only today).
4. **Backend:** CDN and HTTP caching, rate limiting, stateless horizontally scaled APIs, token refresh.
5. **Observability:** crash and performance monitoring, feature flags and staged rollouts, plus DB indexes and modularization.

## 5. Second Platform (iOS/macOS)
Build it natively with SwiftUI using the same layers: SwiftUI views → `@Observable` view model → Repository → `URLSession` (async/await) + SwiftData/Core Data as the single source of truth. Tokens go in the **Keychain**, dependencies are injected through protocols, and background sync uses BGTaskScheduler. macOS reuses the same SwiftUI code with a Mac target. Alternatively, Kotlin Multiplatform could share the repository and domain layers while keeping native UIs.
