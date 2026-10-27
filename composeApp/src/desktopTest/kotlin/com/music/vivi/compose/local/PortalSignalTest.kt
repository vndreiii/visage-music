package com.music.vivi.compose.local

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusSigHandler
import kotlin.test.Test

/** dbus-java rejects a signal class that is not a member of its interface; subscribing is what threw before. */
class PortalSignalTest {
    @Test fun `request response signal can be subscribed`() {
        val conn = runCatching { DBusConnectionBuilder.forSessionBus().withShared(false).build() }.getOrNull() ?: return // no session bus here
        try {
            val handler = DBusSigHandler<PortalRequest.Response> { }
            conn.addSigHandler(PortalRequest.Response::class.java, handler)
            conn.removeSigHandler(PortalRequest.Response::class.java, handler)
        } finally {
            conn.disconnect()
        }
    }
}
