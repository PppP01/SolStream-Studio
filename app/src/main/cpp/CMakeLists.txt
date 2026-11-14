# SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
# Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
# SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

cmake_minimum_required(VERSION 3.22.1)
project("sol_rnnoise" LANGUAGES C)

set(RNNOISE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/rnnoise")

if(EXISTS "${RNNOISE_ROOT}/src/denoise.c" AND EXISTS "${RNNOISE_ROOT}/src/rnn_data.c")
    message(STATUS "SolStream NDK: Compiling official Xiph 48kHz RNNoise neural model")
    set(RNNOISE_SRCS
        "${RNNOISE_ROOT}/src/denoise.c"
        "${RNNOISE_ROOT}/src/rnn.c"
        "${RNNOISE_ROOT}/src/rnn_data.c"
        "${RNNOISE_ROOT}/src/pitch.c"
        "${RNNOISE_ROOT}/src/kiss_fft.c"
        "${RNNOISE_ROOT}/src/celt_lpc.c"
    )
    if(EXISTS "${RNNOISE_ROOT}/src/rnn_reader.c")
        list(APPEND RNNOISE_SRCS "${RNNOISE_ROOT}/src/rnn_reader.c")
    endif()

    add_library(sol_rnnoise SHARED
        sol_rnnoise_jni.c
        ${RNNOISE_SRCS}
    )
    target_include_directories(sol_rnnoise PRIVATE
        "${RNNOISE_ROOT}/include"
        "${RNNOISE_ROOT}/src"
    )
    target_compile_definitions(sol_rnnoise PRIVATE
        RNNOISE_BUILD
        SOL_USE_XIPH_RNNOISE=1
    )
else()
    message(STATUS "SolStream NDK: Compiling built-in C 48kHz Bark-MMSE fallback denoiser")
    add_library(sol_rnnoise SHARED
        sol_rnnoise_jni.c
    )
    target_compile_definitions(sol_rnnoise PRIVATE
        SOL_USE_XIPH_RNNOISE=0
    )
endif()

# Standard IEEE-754 -O3 (without -ffast-math, which Xiph arch.h blocks)
# Includes -Wno-null-dereference to silence upstream Xiph rnnoise/src/rnn.c assert macro warnings
target_compile_options(sol_rnnoise PRIVATE
    -O3
    -std=gnu11
    -fvisibility=hidden
    -Wno-unused-parameter
    -Wno-unused-function
    -Wno-sign-compare
    -Wno-null-dereference
)

# Enforce 16 KB ELF LOAD segment alignment for Android 15–18+ compliance
target_link_options(sol_rnnoise PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

target_link_libraries(sol_rnnoise PRIVATE
    m
    log
)
