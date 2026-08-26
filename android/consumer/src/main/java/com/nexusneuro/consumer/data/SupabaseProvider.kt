package com.nexusneuro.consumer.data

import com.nexusneuro.consumer.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object AuthDeepLink {
    const val SCHEME = "com.nexusneuro.consumer"
    const val HOST = "login-callback"
    const val REDIRECT = "$SCHEME://$HOST"
}

object SupabaseProvider {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(json)
            install(Auth) {
                // Email verify / magic links open the Android app instead of localhost:3000
                scheme = AuthDeepLink.SCHEME
                host = AuthDeepLink.HOST
            }
            install(Postgrest)
        }
    }
}
