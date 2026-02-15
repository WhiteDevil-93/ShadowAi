# Minimal CMakeLists for inference process
# This builds the llama_jni library using the same source configuration as the app module

cmake_minimum_required(VERSION 3.22.1)

project("llama_jni_isolated")

# Use the same C++ standard as the app module
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define paths to llama.cpp sources in the app module (shared with app build)
set(ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../)
set(LLAMA_DIR ${ROOT_DIR}/app/src/main/cpp/llama)
set(GGML_DIR ${ROOT_DIR}/app/src/main/cpp/llama/ggml)
set(MODELS_DIR ${ROOT_DIR}/app/src/main/cpp/llama/src/models)

# Include essential llama.cpp source files (same as app module)
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

# Include essential ggml source files (same as app module)
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

# Include essential ggml-cpu source files for ARM (same as app module)
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

# Create static library for llama.cpp core (same as app module)
add_library(llama_isolated STATIC
    ${LLAMA_SOURCES}
    ${GGML_SOURCES}
    ${GGML_CPU_SOURCES}
    ${MODEL_SOURCES}
)

target_include_directories(llama_isolated PRIVATE
    ${LLAMA_DIR}/include
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
    ${GGML_DIR}/src/ggml-cpu/arch/arm
    ${MODELS_DIR}
)

# Define version macros (same as app module)
target_compile_definitions(llama_isolated PRIVATE
    GGML_VERSION="1.0.0"
    GGML_COMMIT="unknown"
)

# Use same compiler options as app module (no aggressive optimizations)
target_compile_options(llama_isolated PRIVATE
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-missing-field-initializers
    -Wno-deprecated-declarations
    -fPIC
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
    -fno-openmp
    -Wno-unknown-pragmas
)

# Add JNI library for isolated process
add_library(llama_jni_iso SHARED llama_jni.cpp)

target_include_directories(llama_jni_iso PRIVATE
    ${LLAMA_DIR}/include
    ${GGML_DIR}/include
)

# Link with isolated llama core and Android logs
target_link_libraries(llama_jni_iso PRIVATE
    llama_isolated
    log
    android
    ${ANDROID_STL_SHARED_LIBRARY}
)

# Use same properties as app module
set_target_properties(llama_jni_iso PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    LINK_FLAGS "-Wl,-z,max-page-size=16384 -fno-openmp"
)

message(STATUS "Isolated llama_jni_iso library configured with shared sources from app module")
