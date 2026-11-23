package com.anilili.ui.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvQrScanAddressTest {
    @Test fun `TV QR on home wifi starts pairing to the advertised port`() {
        val tv = parseScannedTvPairingAddress("192.168.1.50:47001")
        assertEquals("192.168.1.50", tv?.host)
        assertEquals(47001, tv?.port)
        assertEquals("AniLumos TV", tv?.name)
    }

    @Test fun `private IPv4 subnets are supported`() {
        assertEquals(6000, parseScannedTvPairingAddress("10.0.0.8:6000")?.port)
        assertEquals(12345, parseScannedTvPairingAddress("172.16.2.7:12345")?.port)
        assertEquals(54321, parseScannedTvPairingAddress("169.254.1.1:54321")?.port)
    }

    @Test fun `public IP addresses and external URLs never start pairing`() {
        listOf(
            "8.8.8.8:443",
            "https://192.168.1.50:47001",
            "example.com:80",
            "192.168.1.50:47001/path",
            "192.168.1.50:0",
            "192.168.1.50:65536",
            "192.168.1.50:abc",
            "192.168.1.999:5000",
            "192.168.001.50:5000",
            "127.0.0.1:5000",
            "192.168.1.50:5000  (wlan0)",
            "",
        ).forEach { assertNull("Should reject $it", parseScannedTvPairingAddress(it)) }
    }
}
