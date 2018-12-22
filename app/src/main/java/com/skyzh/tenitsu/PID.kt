package com.skyzh.tenitsu

import org.bytedeco.javacpp.RealSense.error


class PID(val Kp: Double, val Ki: Double, val Kd: Double, val outputMin: Double, val outputMax: Double) {
    fun clamp(data: Double, min: Double, max: Double): Double {
        if (data < min) return min
        if (data > max) return max
        return data
    }

    var total_error = 0.0
    var prev_error = 0.0

    fun reset() {
        total_error = 0.0
        prev_error = 0.0
    }

    fun calc(error: Double): Double {
        if (Ki != 0.0) {
            total_error = clamp(total_error + error,
                    outputMin / Ki,
                    outputMax / Ki)
        }
        val result = Kp * error + Ki * total_error + Kd * (error - prev_error)
        prev_error = error
        return clamp(result, outputMin, outputMax)
    }
}