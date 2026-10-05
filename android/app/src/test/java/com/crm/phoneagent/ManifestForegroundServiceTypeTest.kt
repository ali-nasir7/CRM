package com.crm.phoneagent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression guard for the crash that made the Companion app die immediately after pairing:
 * PhoneAgentConnectionService declared foregroundServiceType="phoneCall" while never registering
 * the call with TelecomManager/ConnectionService (CallExecutor only fires ACTION_CALL and lets the
 * system dialer own the call). On Android 14+ that combination makes startForeground() throw
 * ForegroundServiceStartNotAllowedException. Plain JVM/XML test - no emulator needed.
 */
class ManifestForegroundServiceTypeTest {

    private val manifest by lazy {
        val file = File("src/main/AndroidManifest.xml")
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    @Test
    fun connectionServiceIsNotDeclaredAsAnOngoingPhoneCall() {
        val services = manifest.getElementsByTagName("service")
        val connectionService = (0 until services.length)
            .map { services.item(it) }
            .first { it.attributes.getNamedItem("android:name")?.nodeValue?.endsWith("PhoneAgentConnectionService") == true }

        val type = connectionService.attributes.getNamedItem("android:foregroundServiceType")?.nodeValue
        assertFalse(
            "PhoneAgentConnectionService must not declare the 'phoneCall' FGS type unless it registers " +
                "the call via TelecomManager/ConnectionService - see PhoneAgentConnectionService KDoc",
            type == "phoneCall",
        )
    }

    @Test
    fun manifestDoesNotRequestTheUnusedPhoneCallForegroundServicePermission() {
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map { permissions.item(it).attributes.getNamedItem("android:name").nodeValue }
        assertTrue(
            "FOREGROUND_SERVICE_PHONE_CALL should not be requested unless the service is actually " +
                "declared with the phoneCall type",
            "android.permission.FOREGROUND_SERVICE_PHONE_CALL" !in names,
        )
    }
}
