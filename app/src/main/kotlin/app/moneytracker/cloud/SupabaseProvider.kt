package app.moneytracker.cloud

import app.moneytracker.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single lazily-built Supabase client. URL and anon key come from BuildConfig
 * which is populated from local.properties at build time (never committed).
 *
 * The anon key is public-by-design — security comes from RLS + per-user JWTs,
 * not key secrecy (PLAN.md §4, §21.4).
 */
object SupabaseProvider {

    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL missing — set it in local.properties"
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY missing — set it in local.properties"
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
