package com.shadowai.app.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificatePinningConfigTest {

    @Test
    fun `configured pins use explicit algorithm prefix`() {
        CertificatePinningConfig.HOST_PINS.forEach { (host, pins) ->
            pins.forEach { pin ->
                assertTrue(
                    "Pin for $host must start with sha256/ or sha1/: $pin",
                    pin.startsWith("sha256/") || pin.startsWith("sha1/")
                )
            }
        }
    }

    @Test
    fun `createPinner builds for all configured hosts`() {
        val pinner = CertificatePinningConfig.createPinner()
        assertNotNull(pinner)
    }

    @Test
    fun `createPinnerForHost builds for each configured host`() {
        CertificatePinningConfig.HOST_PINS.keys.forEach { host ->
            val pinner = CertificatePinningConfig.createPinnerForHost(host)
            assertNotNull("Expected pinner for host: $host", pinner)
        }
    }
}
