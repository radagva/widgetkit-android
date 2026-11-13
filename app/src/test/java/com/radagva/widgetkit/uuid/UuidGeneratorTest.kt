package com.radagva.widgetkit.uuid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UuidGeneratorTest {

    private val now = 1_763_000_000_000L // a fixed instant (Nov 2025)

    @Before
    fun resetClock() = UuidGenerator.resetClockForTests()

    @Test
    fun everyVersionSetsItsVersionAndRfcVariant() {
        UuidVersion.entries.forEach { version ->
            repeat(50) {
                val uuid = UuidGenerator.generate(version, now)
                assertEquals("version of $uuid", version.number, uuid.version())
                assertEquals("variant of $uuid", 2, uuid.variant())
            }
        }
    }

    @Test
    fun v7EmbedsTheUnixMillisecondTimestamp() {
        val uuid = UuidGenerator.generate(UuidVersion.V7, now)
        assertEquals(now, uuid.mostSignificantBits ushr 16)
    }

    @Test
    fun v1EmbedsTheGregorianTimestamp() {
        val uuid = UuidGenerator.generate(UuidVersion.V1, now + 5_000)
        // java.util.UUID decodes v1 timestamps (100 ns since 1582-10-15).
        val expected = (now + 5_000) * 10_000 + 0x01B21DD213814000L
        assertTrue(uuid.timestamp() >= expected && uuid.timestamp() < expected + 1_000)
    }

    @Test
    fun v6HoldsTheSameTimestampAsV1ReorderedToSort() {
        val uuid = UuidGenerator.generate(UuidVersion.V6, now + 10_000)
        val msb = uuid.mostSignificantBits
        val timestamp = ((msb ushr 32) shl 28) or (((msb ushr 16) and 0xFFFF) shl 12) or (msb and 0x0FFF)
        val expected = (now + 10_000) * 10_000 + 0x01B21DD213814000L
        assertTrue(timestamp >= expected && timestamp < expected + 1_000)
    }

    @Test
    fun timeOrderedVersionsSortByCreationTime() {
        listOf(UuidVersion.V6, UuidVersion.V7).forEach { version ->
            val generated = (0 until 200).map { UuidGenerator.generate(version, now + 20_000 + it).toString() }
            assertEquals("$version sorts by time", generated, generated.sorted())
        }
    }

    @Test
    fun valuesAreUnique() {
        UuidVersion.entries.forEach { version ->
            val values = (0 until 2_000).map { UuidGenerator.generate(version, now + 30_000).toString() }.toSet()
            assertEquals("$version uniqueness", 2_000, values.size)
        }
    }
}
