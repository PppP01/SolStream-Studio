/*
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

/**
 * Zero-allocation Kotlin JNI bridge to libsol_rnnoise.so (48 kHz 480-sample C RNNoise engine
 * compiled with 16 KB ELF page alignment for Android 10–17+).
 *
 * Gracefully degrades to false if native library is not yet compiled or during JVM unit tests.
 */
object SolNativeRnnoise {

    val isLibraryLoaded: Boolean = try {
        System.loadLibrary("sol_rnnoise")
        true
    } catch (_: Throwable) {
        false
    }

    val isNeuralModelReady: Boolean
        get() = if (isLibraryLoaded) {
            try {
                nativeIsNeuralModelActive()
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }

    fun createInstance(): Long {
        if (!isLibraryLoaded) return 0L
        return try {
            nativeCreate()
        } catch (_: Throwable) {
            0L
        }
    }

    fun destroyInstance(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) return
        try {
            nativeDestroy(handle)
        } catch (_: Throwable) {
        }
    }

    /**
     * Processes exactly 480 mono float samples (10 ms @ 48 kHz) in-place with zero heap allocations.
     *
     * @param handle Native DenoiseState pointer from [createInstance].
     * @param frame480 Mono float buffer in [-1.0f, 1.0f] scale (length >= 480).
     * @param strength Wet/dry blend in [0.0f..1.0f] (e.g., 0.65f Gentle, 0.88f Natural, 1.0f Max).
     * @return Neural VAD speech probability in [0.0f..1.0f], or -1.0f if unavailable.
     */
    fun processMono480InPlace(handle: Long, frame480: FloatArray, strength: Float): Float {
        if (!isLibraryLoaded || handle == 0L || frame480.size < 480) return -1.0f
        return try {
            nativeProcessFrame480(handle, frame480, strength.coerceIn(0.0f, 1.0f))
        } catch (_: Throwable) {
            -1.0f
        }
    }

    fun engineDescription(): String {
        return when {
            !isLibraryLoaded -> "Kotlin Spectral Cut (Native C Pending)"
            isNeuralModelReady -> "Native C 48kHz Xiph RNNoise (16KB-Aligned)"
            else -> "Native C 48kHz Bark-MMSE Denoiser (16KB-Aligned)"
        }
    }

    @JvmStatic
    private external fun nativeIsNeuralModelActive(): Boolean

    @JvmStatic
    private external fun nativeCreate(): Long

    @JvmStatic
    private external fun nativeDestroy(handle: Long)

    @JvmStatic
    private external fun nativeProcessFrame480(
        handle: Long,
        inOutFrame480: FloatArray,
        wetStrength: Float
    ): Float
}
