cmake_minimum_required(VERSION 3.22.1)
project("shadowai")

# Android specific settings
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- Windows-safe Android preset ----
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_COMMON OFF)

set(GGML_BUILD_TESTS OFF)
set(GGML_BUILD_EXAMPLES OFF)

# Disable every model backend except llama
set(LLAMA_BUILD_LLAMA ON)

set(LLAMA_BUILD_FALCON OFF)
set(LLAMA_BUILD_GEMMA OFF)
set(LLAMA_BUILD_GEMMA2 OFF)
set(LLAMA_BUILD_GEMMA3 OFF)
set(LLAMA_BUILD_RWKV OFF)
set(LLAMA_BUILD_DEEPSEEK OFF)
set(LLAMA_BUILD_MAMBA OFF)
set(LLAMA_BUILD_COMMAND_R OFF)
set(LLAMA_BUILD_BERT OFF)
set(LLAMA_BUILD_CHATGLM OFF)
set(LLAMA_BUILD_QWEN OFF)
set(LLAMA_BUILD_MOE OFF)

# OpenMP is DISABLED for Android - using native threading instead
# ggml.cpp uses its own thread pool for parallelization
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP for Android" FORCE)
set(LLAMA_OPENMP OFF CACHE BOOL "Disable OpenMP for Android" FORCE)

# Explicitly prevent OpenMP from being found/linked
set(OpenMP_C_FOUND FALSE)
set(OpenMP_CXX_FOUND FALSE)

if(DEFINED LLAMA_OPENMP AND LLAMA_OPENMP)
    message(
        STATUS
        "LLAMA_OPENMP was requested (${LLAMA_OPENMP}), but Android builds always use native threading."
    )
endif()

# Windows + Ninja safety (Prevents log locking issues)
# Note: This is usually an environment variable, but setting it here doesn't hurt.
set(CMAKE_BUILD_PARALLEL_LEVEL 1)

# Set source directories
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama)
set(GGML_DIR ${CMAKE_SOURCE_DIR}/llama/ggml)
set(MODELS_DIR ${CMAKE_SOURCE_DIR}/llama/src/models)

# Include essential llama.cpp source files for basic functionality
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

# Create static library
add_library(llama STATIC ${LLAMA_SOURCES} ${GGML_SOURCES} ${GGML_CPU_SOURCES} ${MODEL_SOURCES})

target_include_directories(llama PRIVATE
    ${LLAMA_DIR}/include
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
    ${GGML_DIR}/src/ggml-cpu/arch/x86
    ${MODELS_DIR}
)

# Define version macros
target_compile_definitions(llama PRIVATE
    GGML_VERSION=\"1.0.0\"
    GGML_COMMIT=\"unknown\"
)

# Suppress warnings and add security hardening
target_compile_options(llama PRIVATE
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

# Threading is handled by ggml's internal thread pool (see ggml.cpp ggml_thread_pool)
# No need for OpenMP on Android - using std::thread and android::Thread instead
message(STATUS "Using native Android threading (no OpenMP)")

# Create JNI library
add_library(llama_jni SHARED llama_jni.cpp)
target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${GGML_DIR}/include
)

# Link with STL - critical for NDK 27 + c++_shared
target_link_libraries(llama_jni PRIVATE 
    llama 
    log 
    android
    ${ANDROID_STL_SHARED_LIBRARY}  # Explicitly link libc++_shared
)

set_target_properties(llama_jni PROPERTIES 
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    # Removed -static-libstdc++ - we use c++_shared
    LINK_FLAGS "-Wl,-z,max-page-size=16384 -fno-openmp"
)

# Ensure libc++_shared.so is bundled (Android Gradle Plugin should do this automatically)
# If missing at runtime, check that packaging.jniLibs includes libc++_shared.so
