# Supabase Moon

An Android app (Kotlin, Jetpack Compose, Coroutines) for testing every major Supabase feature against a real project: Auth, Database, Storage, Realtime, Edge Functions and Postgres RPC.

Every call is recorded in an in-app log, and a one-tap diagnostic checks each service.

| Feature | What you can test |
|---|---|
| Auth | Email/password sign-up and sign-in, magic link / OTP, anonymous, GitHub OAuth, password reset, session refresh, update password/metadata, sign out |
| Database | CRUD on `public.notes` through PostgREST with row-level security: insert, select (search, filter, order, limit), update, delete |
| Storage | Upload, list, image preview, signed URL, download, delete on `test-bucket` |
| Realtime | Postgres changes on `notes`, Broadcast chat, Presence (who is online) |
| Functions | Invoke any Edge Function with a JSON body |
| RPC | Call any Postgres function (`add_numbers`, `hello_world`) |
| Logs | Timestamped record of every request and result |

## Stack

- Kotlin 2.2, Jetpack Compose (Material 3), Navigation Compose
- Coroutines and StateFlow, with ViewModels (MVVM)
- [supabase-kt](https://github.com/supabase-community/supabase-kt) 3.2.6 (Auth, Postgrest, Storage, Realtime, Functions), Ktor OkHttp engine
- Coil 3 for image preview
- AGP 8.13, Gradle 8.14, minSdk 26, targetSdk 36

## Setup

1. **Create `local.properties`** in the project root (it is git-ignored):
   ```properties
   sdk.dir=/path/to/Android/sdk
   supabase.url=https://<project-ref>.supabase.co
   supabase.key=<publishable key or anon key>
   ```
   Use only the publishable/anon key. Never put the secret or `service_role` key in an app: anything in an APK can be extracted.
2. **Create the backend objects.** In the Supabase dashboard open *SQL Editor* and run [`supabase/setup.sql`](supabase/setup.sql). It is safe to re-run and creates:
   - the `notes` table, its RLS policies, and Realtime publication
   - the `add_numbers` and `hello_world` SQL functions
   - the public `test-bucket` bucket with storage policies
3. **Dashboard settings** (Authentication):
   - Enable *Anonymous sign-ins* to test that method.
   - Enable the *GitHub* provider to test OAuth.
   - Add `supabasemoon://auth` to *Redirect URLs* (needed for magic link, password reset and OAuth).
4. **(Optional) Deploy the sample Edge Function:**
   ```bash
   supabase functions deploy hello
   ```
5. **Run:** open in Android Studio and press Run, or:
   ```bash
   ./gradlew installDebug
   ```

## Suggested test walkthrough

1. **Home → Run all checks.** Auth service should be green. Database, RPC and Storage turn green once `setup.sql` has run.
2. **Auth →** sign up or sign in (or continue anonymously) so RLS-protected features work.
3. **Data →** add a note, tick it, rename it, search it, delete it.
4. **Live →** tap *Subscribe*, then edit notes on the Data tab and watch events arrive. Join the room on two devices to test Broadcast and Presence.
5. **Files →** upload an image, view it, get a signed URL, delete it.
6. **Functions & RPC →** call `add_numbers` with `{"a":2,"b":3}`; invoke `hello` with `{"name":"Moon"}`.
7. **Logs** show everything that happened.

## Project layout

```
app/src/main/java/com/moon/supabasetester/
├── MainActivity.kt        # entry point, navigation, deep-link handling
├── data/
│   ├── SupabaseProvider.kt  # the single Supabase client + plugin install
│   ├── AppLog.kt            # in-app log + logged{} wrapper
│   └── Models.kt            # Note, ChatMessage, PresenceState
└── ui/
    ├── theme/Theme.kt
    ├── components/Common.kt # SectionCard, Field, BusyButton, ResultBox…
    └── screens/             # Home, Auth, Database, Storage, Realtime, Functions, Logs
supabase/
├── setup.sql              # schema, RLS, RPC, storage bucket
└── functions/hello/       # sample Edge Function
docs/ARCHITECTURE.md       # flows and internals
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the app works and the flow of each feature.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Could not find the table 'public.notes'` (PGRST205) | `setup.sql` has not been run |
| `new row violates row-level security policy` | Not signed in (insert/upload need an authenticated user) |
| Realtime shows no events | `notes` is not in the `supabase_realtime` publication (rerun `setup.sql`) or you are not subscribed |
| Function returns 404 | The Edge Function is not deployed |
| Magic link opens a browser instead of the app | `supabasemoon://auth` missing from Redirect URLs |
| Build says dependency needs a newer AGP/Kotlin | Versions are pinned in `gradle/libs.versions.toml`; supabase-kt 3.8+ needs Kotlin 2.4 |
