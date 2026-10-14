package com.music.innertube.auth

import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeLocale
import com.music.innertube.models.body.AccountMenuBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LoginContextTest {
    @Test
    fun languageOnlyJvmLocaleProducesValidLoginContext() {
        val context = YouTubeClient.WEB_REMIX.toContext(YouTubeLocale("", "en"), null, "")
        assertEquals("US", context.client.gl)
        assertEquals(null, context.user.onBehalfOfUser)
        val payload = Json { encodeDefaults = true; explicitNulls = false }.encodeToString(AccountMenuBody(context))
        assertFalse(payload.contains("DEVICE_THEME_SELECTED"))
        assertFalse(payload.contains("onBehalfOfUser"))
    }
}
