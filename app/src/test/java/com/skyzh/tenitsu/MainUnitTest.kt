package com.skyzh.tenitsu

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class MainUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun transmission_builder() {
        var builder = TransmissionBuilder()
        assertArrayEquals(
                byteArrayOf(0, 4, 0, 0, 233.toByte(), 0, 2.toByte(), (0 xor 4 xor 0 xor 0 xor 233 xor 0 xor 2).toByte()),
                builder.build_message(1024, 233, 2)
        )

        assertArrayEquals(
                byteArrayOf(1, (-1).toByte(), (-1).toByte(), (-1).toByte(), (-2).toByte(), (-1).toByte(), (-1).toByte(), (1 xor -1 xor -1 xor -1 xor -2 xor -1 xor -1).toByte()),
                builder.build_message(-1, -2, -1)
        )

    }
}
