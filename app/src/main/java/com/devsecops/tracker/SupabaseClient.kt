package com.devsecops.tracker

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

// Single shared client for the whole app.
// Values come from BuildConfig, generated from app/build.gradle.kts.
object Supa {
    // encodeDefaults: fields that equal their default (rubric_passed=false,
    // rest=false, ...) MUST still be sent, otherwise an update/upsert would
    // silently skip them.
    // coerceInputValues: an explicit JSON null for a non-null field that has a
    // default falls back to the default instead of throwing.
    // ignoreUnknownKeys: tables have more columns than the models.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        defaultSerializer = KotlinXSerializer(json)
        install(Auth)
        install(Postgrest)
    }
}
