package cn.verlu.cloud.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

fun createCloudSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = SupabaseConfig.URL,
        supabaseKey = SupabaseConfig.ANON_KEY,
    ) {
        install(Auth) {
            scheme = "verlucloud"
            host = "login"
            flowType = FlowType.PKCE
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
