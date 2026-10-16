package com.moon.supabasetester.data

import com.moon.supabasetester.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

const val DEEPLINK_SCHEME = "supabasemoon"
const val DEEPLINK_HOST = "auth"
const val BUCKET = "test-bucket"

/** Single shared client. Uses only the publishable key; row access is governed by RLS. */
object SupabaseProvider {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY,
        ) {
            install(Auth) {
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_HOST
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }
}
