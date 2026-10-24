package com.singam.lionlibrary.di

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.singam.lionlibrary.data.remote.api.SkipDbApiService
import com.singam.lionlibrary.data.remote.api.TheIntroDbApiService
import com.singam.lionlibrary.util.Constants
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import retrofit2.Retrofit

/**
 * Break vector: skip-time qualifiers resolving to null (for example when declared after the
 * module), which silently turns their clients into unqualified overrides of TMDB's.
 */
class NetworkModuleTest {
    @Test
    fun `skip-time clients are qualified and never replace the TMDB client`() {
        val koin = koinApplication {
            allowOverride(false)
            modules(networkModule)
        }
            .koin
        assertThat(koin.get<Retrofit>().baseUrl().toString()).isEqualTo(Constants.TMDB_BASE_URL)
        assertThat(koin.get<TheIntroDbApiService>()).isNotNull()
        assertThat(koin.get<SkipDbApiService>()).isNotNull()
    }
}
