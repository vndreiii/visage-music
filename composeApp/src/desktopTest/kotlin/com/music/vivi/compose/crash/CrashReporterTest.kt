package com.music.vivi.compose.crash

import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReporterTest {
    private fun uiError() = IllegalStateException("ui") // one throw site, like a failure repeating every frame

    @Test
    fun explainsCommonFailuresInPlainLanguage() {
        assertTrue(CrashReporter.describe(OutOfMemoryError()).first.contains("memory"))
        assertTrue(CrashReporter.describe(UnsatisfiedLinkError("libmpv")).first.contains("missing"))
        assertTrue(CrashReporter.describe(RuntimeException("wrap", java.net.UnknownHostException("x"))).first.contains("network"))
        assertTrue(CrashReporter.describe(IllegalStateException("boom")).first.contains("Something went wrong"))
    }

    @Test
    fun onlyUiThreadErrorsInterrupt() {
        CrashReporter.dismiss()
        CrashReporter.record("DefaultDispatcher-worker-3", IllegalStateException("background"), fatal = CrashReporter.isUiThread("DefaultDispatcher-worker-3"))
        assertNull(CrashReporter.current.value)
        CrashReporter.record("AWT-EventQueue-0", uiError(), fatal = CrashReporter.isUiThread("AWT-EventQueue-0"))
        assertNotNull(CrashReporter.current.value)
        // The same error repeating immediately must not replace/restack the dialog.
        val id = CrashReporter.current.value!!.id
        CrashReporter.record("AWT-EventQueue-0", uiError(), fatal = true)
        assertEquals(id, CrashReporter.current.value!!.id)
        CrashReporter.dismiss()
    }

    @Test
    fun reportAndIssueUrlCarryTheError() {
        CrashReporter.dismiss()
        CrashReporter.record("AWT-EventQueue-0", IllegalArgumentException("bad thing"), fatal = true)
        val report = CrashReporter.current.value!!
        val text = CrashReporter.fullReport(report)
        assertTrue("IllegalArgumentException: bad thing" in text && "Last log lines" in text)
        val url = CrashReporter.issueUrl(report)
        assertTrue(url.startsWith(CrashReporter.ISSUES_URL))
        assertTrue("bad thing" in URLDecoder.decode(url, Charsets.UTF_8))
        assertTrue(url.length < 4000)
        CrashReporter.dismiss()
    }
}
