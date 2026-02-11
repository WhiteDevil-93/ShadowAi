cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define paths to llama.cpp sources in the app module
set(ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../)
set(LLAMA_DIR ${ROOT_DIR}/app/src/main/cpp/llama)
set(GGML_DIR ${ROOT_DIR}/app/src/main/cpp/llama/ggml)
set(MODELS_DIR ${ROOT_DIR}/app/src/main/cpp/llama/src/models)

# Include essential llama.cpp source files
set(LLAMA_SOURCES
    ${LLAMA_DIR}/src/llama.cpp
    ${LLAMA_DIR}/src/llama-impl.cpp
    ${LLAMA_DIR}/src/llama-arch.cpp
    ${LLAMA_DIR}/src/llama-batch.cpp
    ${LLAMA_DIR}/src/llama-chat.cpp
    ${LLAMA_DIR}/src/llama-context.cpp
    ${LLAMA_DIR}/src/llama-cparams.cpp
    ${LLAMA_DIR}/src/llama-grammar.cpp
    ${LLAMA_DIR}/src/llama-graph.cpp
    ${LLAMA_DIR}/src/llama-hparams.cpp
    ${LLAMA_DIR}/src/llama-io.cpp
    ${LLAMA_DIR}/src/llama-kv-cache.cpp
    ${LLAMA_DIR}/src/llama-kv-cache-iswa.cpp
    ${LLAMA_DIR}/src/llama-memory.cpp
    ${LLAMA_DIR}/src/llama-memory-recurrent.cpp
    ${LLAMA_DIR}/src/llama-memory-hybrid.cpp
    ${LLAMA_DIR}/src/llama-mmap.cpp
    ${LLAMA_DIR}/src/llama-model-loader.cpp
    ${LLAMA_DIR}/src/llama-model-saver.cpp
    ${LLAMA_DIR}/src/llama-model.cpp
    ${LLAMA_DIR}/src/llama-quant.cpp
    ${LLAMA_DIR}/src/llama-sampling.cpp
    ${LLAMA_DIR}/src/llama-vocab.cpp
    ${LLAMA_DIR}/src/llama-adapter.cpp
    ${LLAMA_DIR}/src/unicode-data.cpp
    ${LLAMA_DIR}/src/unicode.cpp
)

# Include essential ggml source files
set(GGML_SOURCES
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml.cpp
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-backend.cpp
    ${GGML_DIR}/src/ggml-backend-reg.cpp
    ${GGML_DIR}/src/ggml-opt.cpp
    ${GGML_DIR}/src/ggml-quants.c
    ${GGML_DIR}/src/ggml-threading.cpp
    ${GGML_DIR}/src/gguf.cpp
)

# Include essential ggml-cpu source files for ARM
set(GGML_CPU_SOURCES
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
    ${GGML_DIR}/src/ggml-cpu/quants.c
    ${GGML_DIR}/src/ggml-cpu/repack.cpp
    ${GGML_DIR}/src/ggml-cpu/ops.cpp
    ${GGML_DIR}/src/ggml-cpu/vec.cpp
    ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
    ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
    ${GGML_DIR}/src/ggml-cpu/traits.cpp
    ${GGML_DIR}/src/ggml-cpu/arch/arm/cpu-feats.cpp
    ${GGML_DIR}/src/ggml-cpu/arch/arm/quants.c
    ${GGML_DIR}/src/ggml-cpu/arch/arm/repack.cpp
)

file(GLOB MODEL_SOURCES "${MODELS_DIR}/*.cpp")

# Create static library for llama.cpp core
add_library(llama_core STATIC 
    ${LLAMA_SOURCES} 
    ${GGML_SOURCES} 
    ${GGML_CPU_SOURCES} 
    ${MODEL_SOURCES}
)

target_include_directories(llama_core PRIVATE
    ${LLAMA_DIR}/include
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
    ${GGML_DIR}/src/ggml-cpu/arch/arm
    ${MODELS_DIR}
)

# Define version macros
target_compile_definitions(llama_core PRIVATE
    GGML_VERSION="1.0.0"
    GGML_COMMIT="unknown"
)

# Optimization and security flags
target_compile_options(llama_core PRIVATE
    -O3
    -ffast-math
    -fPIC
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-unknown-pragmas
)

# Add our JNI library
add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${GGML_DIR}/include
    ${GGML_DIR}/src
)

# Link JNI library with llama_core and Android logs
target_link_libraries(llama_jni
    llama_core
    log
    android
)

# ARM64 specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(llama_jni PRIVATE
        -march=armv8-a+fp16+dotprod
        -O3
        -DGGML_USE_NEON
    )
endif()

message(STATUS "llama_jni library configured with llama.cpp sources from app module")
