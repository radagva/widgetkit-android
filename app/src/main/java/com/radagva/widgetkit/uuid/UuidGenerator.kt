package com.radagva.widgetkit.uuid

import java.security.SecureRandom
import java.util.UUID

/** UUID versions that need no input (RFC 9562). v3/v5 need a namespace + name, so they're not offered. */
enum class UuidVersion(val number: Int) {
    V1(1), // Gregorian time + random node
    V4(4), // random
    V6(6), // Gregorian time, reordered so values sort by time
    V7(7), // Unix time in milliseconds, sortable
}

/** Generates RFC 9562 UUIDs. Thread-safe. */
object UuidGenerator {

    private val random = SecureRandom()

    /** 100 ns intervals between 1582-10-15 (Gregorian epoch) and 1970-01-01. */
    private const val GREGORIAN_OFFSET = 0x01B21DD213814000L

    // v1/v6: random clock sequence and node (multicast bit set = not a real MAC address).
    private val clockSequence = random.nextInt(1 shl 14).toLong()
    private val node = (random.nextLong() and 0xFFFFFFFFFFFFL) or 0x010000000000L
    private var lastGregorian = 0L

    fun generate(version: UuidVersion, nowMillis: Long = System.currentTimeMillis()): UUID = when (version) {
        UuidVersion.V1 -> v1(gregorianTimestamp(nowMillis))
        UuidVersion.V4 -> UUID.randomUUID()
        UuidVersion.V6 -> v6(gregorianTimestamp(nowMillis))
        UuidVersion.V7 -> v7(nowMillis)
    }

    /** Tests only: forget the last v1/v6 timestamp so tests don't depend on each other's order. */
    @Synchronized
    internal fun resetClockForTests() {
        lastGregorian = 0
    }

    /** 60-bit timestamp in 100 ns units since 1582; strictly increasing so values never repeat. */
    @Synchronized
    private fun gregorianTimestamp(nowMillis: Long): Long {
        var timestamp = nowMillis * 10_000 + GREGORIAN_OFFSET
        if (timestamp <= lastGregorian) timestamp = lastGregorian + 1
        lastGregorian = timestamp
        return timestamp and 0x0FFFFFFFFFFFFFFFL
    }

    /** Variant 10 + 14-bit clock sequence + 48-bit node (shared by v1 and v6). */
    private fun timeBasedLeastBits(): Long = ((0x8000L or clockSequence) shl 48) or node

    // time_low (32) | time_mid (16) | version (4) | time_high (12)
    private fun v1(timestamp: Long): UUID {
        val timeLow = timestamp and 0xFFFFFFFFL
        val timeMid = (timestamp ushr 32) and 0xFFFFL
        val timeHigh = (timestamp ushr 48) and 0x0FFFL
        val msb = (timeLow shl 32) or (timeMid shl 16) or (0x1L shl 12) or timeHigh
        return UUID(msb, timeBasedLeastBits())
    }

    // time_high (32) | time_mid (16) | version (4) | time_low (12): most significant first, so it sorts.
    private fun v6(timestamp: Long): UUID {
        val timeHigh = timestamp ushr 28
        val timeMid = (timestamp ushr 12) and 0xFFFFL
        val timeLow = timestamp and 0x0FFFL
        val msb = (timeHigh shl 32) or (timeMid shl 16) or (0x6L shl 12) or timeLow
        return UUID(msb, timeBasedLeastBits())
    }

    // unix_ts_ms (48) | version (4) | rand_a (12) | variant (2) | rand_b (62)
    private fun v7(nowMillis: Long): UUID {
        val msb = ((nowMillis and 0xFFFFFFFFFFFFL) shl 16) or (0x7L shl 12) or random.nextInt(1 shl 12).toLong()
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}
