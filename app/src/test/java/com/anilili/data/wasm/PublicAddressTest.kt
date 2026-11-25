package com.anilili.data.wasm

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PublicAddressTest {
    @Test fun deniesLocalAndSpecialRanges() {
        listOf("127.0.0.1", "10.0.0.1", "192.168.1.2", "169.254.169.254", "100.64.0.1",
            "198.18.0.1", "192.0.2.1", "203.0.113.1", "::1", "fc00::1", "fe80::1",
            "2001:db8::1", "2002::1", "::ffff:127.0.0.1").forEach {
            assertFalse(it, PublicAddress.allowed(InetAddress.getByName(it)))
        }
        assertTrue(PublicAddress.allowed(InetAddress.getByName("8.8.8.8")))
        assertTrue(PublicAddress.allowed(InetAddress.getByName("2606:4700::1111")))
    }
}
