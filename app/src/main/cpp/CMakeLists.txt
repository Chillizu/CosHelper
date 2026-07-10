cmake_minimum_required(VERSION 3.22.1)
project("coshelper_native")

set(CMAKE_CXX_STANDARD 17)

# --- Whisper options ---
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_COREML OFF CACHE BOOL "" FORCE)
set(WHISPER_OPENVINO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_HEXAGON OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)

add_subdirectory(whisper.cpp)

# --- Opus JNI ---
set(OPUS_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
add_library(opus SHARED IMPORTED)
set_target_properties(opus PROPERTIES
    IMPORTED_LOCATION ${OPUS_LIB_DIR}/libopus.so
)

add_library(
    opus_jni
    SHARED
    opus_jni.cpp
)

target_include_directories(opus_jni PRIVATE ${CMAKE_SOURCE_DIR}/opus_include)

target_link_libraries(
    opus_jni
    opus
    log
)

# --- Whisper JNI ---
add_library(
    whisper_jni
    SHARED
    whisper_jni.cpp
)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/whisper.cpp/include
    ${CMAKE_SOURCE_DIR}/whisper.cpp/src
    ${CMAKE_SOURCE_DIR}/whisper.cpp/ggml/include
    ${CMAKE_SOURCE_DIR}/whisper.cpp/ggml/src
)

target_link_libraries(
    whisper_jni
    whisper
    log
)
