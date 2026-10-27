package com.music.vivi.compose.local

import kotlin.test.Test
import kotlin.test.assertEquals

class PortalSignatureTest {
    /** A wrong signature makes the session bus drop the connection, so every file pick silently failed. */
    @Test fun `OpenFile marshals as ssa{sv}`() {
        val m = PortalFileChooser::class.java.methods.first { it.name == "OpenFile" }
        val sig = m.genericParameterTypes.joinToString("") { t ->
            org.freedesktop.dbus.Marshalling.getDBusType(t).joinToString("")
        }
        assertEquals("ssa{sv}", sig)
    }
}
