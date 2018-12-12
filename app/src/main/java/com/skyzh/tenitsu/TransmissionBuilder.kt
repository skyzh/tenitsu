package com.skyzh.tenitsu

class TransmissionBuilder {
    private var message_cnt = 0

    fun build_message(left_wheel: Int, right_wheel: Int, front_motor: Int): ByteArray {
        var bytes = ByteArray(8)
        bytes[0] = message_cnt.toByte()
        bytes[1] = (left_wheel ushr 8).toByte()
        bytes[2] = left_wheel.toByte()
        bytes[3] = (right_wheel ushr 8).toByte()
        bytes[4] = right_wheel.toByte()
        bytes[5] = (front_motor ushr 8).toByte()
        bytes[6] = front_motor.toByte()
        bytes[7] = (0..6).map { bytes[it].toInt() }.reduceRight { a, b -> a xor b }.toByte()
        message_cnt = message_cnt + 1
        return bytes
    }
}
