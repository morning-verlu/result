package cn.verlu.cloud.data.remote

import io.github.jan.supabase.SupabaseClient

object CloudSupabase {
    val client: SupabaseClient by lazy { createCloudSupabaseClient() }
}
