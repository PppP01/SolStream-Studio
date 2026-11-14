/*
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#if SOL_USE_XIPH_RNNOISE
#include "rnnoise.h"
#endif

#define SOL_FRAME_LEN 480

typedef struct {
#if SOL_USE_XIPH_RNNOISE
    DenoiseState *rnnState;
    float dryDelayLine[SOL_FRAME_LEN];
#else
    float noiseFloor;
    float smoothGainLow;
    float smoothGainMid;
    float smoothGainHigh;
    float lpLow;
    float lpMid;
#endif
} SolDenoiserCtx;

static inline float sol_clampf(float v, float lo, float hi) {
    if (!isfinite(v) || fabsf(v) < 1e-20f) return 0.0f;
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

JNIEXPORT jboolean JNICALL
Java_com_solgaming_solstreamstudio_SolNativeRnnoise_nativeIsNeuralModelActive(
    JNIEnv *env,
    jclass clazz
) {
    (void)env;
    (void)clazz;
#if SOL_USE_XIPH_RNNOISE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_solgaming_solstreamstudio_SolNativeRnnoise_nativeCreate(
    JNIEnv *env,
    jclass clazz
) {
    (void)env;
    (void)clazz;
    SolDenoiserCtx *ctx = (SolDenoiserCtx *)calloc(1, sizeof(SolDenoiserCtx));
    if (ctx == NULL) {
        return (jlong)0;
    }
#if SOL_USE_XIPH_RNNOISE
    ctx->rnnState = rnnoise_create(NULL);
    if (ctx->rnnState == NULL) {
        free(ctx);
        return (jlong)0;
    }
#else
    ctx->noiseFloor = 0.0018f;
    ctx->smoothGainLow = 1.0f;
    ctx->smoothGainMid = 1.0f;
    ctx->smoothGainHigh = 1.0f;
#endif
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_com_solgaming_solstreamstudio_SolNativeRnnoise_nativeDestroy(
    JNIEnv *env,
    jclass clazz,
    jlong handle
) {
    (void)env;
    (void)clazz;
    if (handle == 0) return;
    SolDenoiserCtx *ctx = (SolDenoiserCtx *)(intptr_t)handle;
#if SOL_USE_XIPH_RNNOISE
    if (ctx->rnnState != NULL) {
        rnnoise_destroy(ctx->rnnState);
        ctx->rnnState = NULL;
    }
#endif
    free(ctx);
}

JNIEXPORT jfloat JNICALL
Java_com_solgaming_solstreamstudio_SolNativeRnnoise_nativeProcessFrame480(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jfloatArray inOutFrame480,
    jfloat wetStrength
) {
    (void)clazz;
    if (handle == 0 || inOutFrame480 == NULL) {
        return -1.0f;
    }
    jsize len = (*env)->GetArrayLength(env, inOutFrame480);
    if (len < SOL_FRAME_LEN) {
        return -1.0f;
    }

    SolDenoiserCtx *ctx = (SolDenoiserCtx *)(intptr_t)handle;
    float wet = sol_clampf((float)wetStrength, 0.0f, 1.0f);
    float dry = 1.0f - wet;

    /* Zero-allocation stack buffer transfer (avoids JVM array pinning or heap copies every 10 ms) */
    jfloat samples[SOL_FRAME_LEN];
    (*env)->GetFloatArrayRegion(env, inOutFrame480, 0, SOL_FRAME_LEN, samples);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1.0f;
    }

    float vadProb = 0.0f;

#if SOL_USE_XIPH_RNNOISE
    float scaledIn[SOL_FRAME_LEN];
    float scaledOut[SOL_FRAME_LEN];
    const float toInt16Scale = 32767.0f;
    const float invInt16Scale = 1.0f / 32767.0f;

    for (int i = 0; i < SOL_FRAME_LEN; i++) {
        float cleanIn = sol_clampf(samples[i], -1.0f, 1.0f);
        samples[i] = cleanIn;
        scaledIn[i] = cleanIn * toInt16Scale;
    }

    vadProb = rnnoise_process_frame(ctx->rnnState, scaledOut, scaledIn);
    vadProb = sol_clampf(vadProb, 0.0f, 1.0f);

    /*
     * Xiph RNNoise's 960-sample Vorbis window introduces an exact 480-sample (10 ms)
     * overlap-add delay. Blending against dryDelayLine[i] preserves 100% phase coherence
     * so partial wet settings never introduce metallic comb-filtering.
     */
    for (int i = 0; i < SOL_FRAME_LEN; i++) {
        float currentRaw = samples[i];
        float alignedDry = ctx->dryDelayLine[i];
        ctx->dryDelayLine[i] = currentRaw;

        float denoised = sol_clampf(scaledOut[i] * invInt16Scale, -1.0f, 1.0f);
        float outVal = (wet >= 0.999f) ? denoised : (wet * denoised + dry * alignedDry);
        samples[i] = sol_clampf(outVal, -1.0f, 1.0f);
    }
#else
    float sumSq = 0.0f;
    int zeroCross = 0;
    int prevSign = (samples[0] >= 0.0f) ? 1 : -1;

    for (int i = 0; i < SOL_FRAME_LEN; i++) {
        float s = sol_clampf(samples[i], -1.0f, 1.0f);
        samples[i] = s;
        sumSq += s * s;
        int sign = (s >= 0.0f) ? 1 : -1;
        if (sign != prevSign) {
            zeroCross++;
            prevSign = sign;
        }
    }

    float rms = sqrtf(sumSq / (float)SOL_FRAME_LEN);
    if (rms < ctx->noiseFloor * 1.55f) {
        ctx->noiseFloor = 0.965f * ctx->noiseFloor + 0.035f * fmaxf(rms, 0.00012f);
    } else {
        ctx->noiseFloor = fminf(0.035f, ctx->noiseFloor * 1.0003f);
    }

    float snr = rms / fmaxf(ctx->noiseFloor, 0.00015f);
    float zcr = (float)zeroCross / (float)SOL_FRAME_LEN;
    vadProb = sol_clampf((snr - 1.8f) / 4.5f, 0.0f, 1.0f);
    if (zcr > 0.45f) vadProb *= 0.5f;

    /* 3-Band Ephraim-Malah MMSE-inspired Wiener gain estimation */
    float baseClean = sol_clampf((snr - 1.15f) / 3.8f, 0.0f, 1.0f);
    float targetLow = sol_clampf(1.0f - wet * 0.72f * (1.0f - baseClean), 0.14f, 1.0f);
    float targetMid = sol_clampf(1.0f - wet * 0.80f * (1.0f - baseClean), 0.12f, 1.0f);
    float targetHigh = sol_clampf(1.0f - wet * 0.92f * (1.0f - baseClean), 0.06f, 1.0f);

    for (int i = 0; i < SOL_FRAME_LEN; i++) {
        float s = samples[i];
        ctx->smoothGainLow += 0.010f * (targetLow - ctx->smoothGainLow);
        ctx->smoothGainMid += 0.012f * (targetMid - ctx->smoothGainMid);
        ctx->smoothGainHigh += 0.015f * (targetHigh - ctx->smoothGainHigh);

        /* Split into <450 Hz, 450..3800 Hz, and >3800 Hz @ 48 kHz */
        ctx->lpLow = sol_clampf(ctx->lpLow + 0.057f * (s - ctx->lpLow), -1.0f, 1.0f);
        ctx->lpMid = sol_clampf(ctx->lpMid + 0.390f * (s - ctx->lpMid), -1.0f, 1.0f);

        float lowBand = ctx->lpLow;
        float midBand = ctx->lpMid - lowBand;
        float highBand = s - ctx->lpMid;

        float denoised = lowBand * ctx->smoothGainLow +
                         midBand * ctx->smoothGainMid +
                         highBand * ctx->smoothGainHigh;
        samples[i] = sol_clampf(denoised, -1.0f, 1.0f);
    }
#endif

    (*env)->SetFloatArrayRegion(env, inOutFrame480, 0, SOL_FRAME_LEN, samples);
    return vadProb;
}
