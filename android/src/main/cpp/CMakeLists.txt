cmake_minimum_required(VERSION 3.22.1)
project(cardboard_jni VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)
add_compile_options(-Wall -Wextra)

find_library(ANDROID_LIB android)
find_library(GLESv2_LIB GLESv2)
find_library(GLESv3_LIB GLESv3)
find_library(EGL_LIB EGL)
find_library(LOG_LIB log)

# Prebuilt Cardboard SDK native lib (extracted from sdk-release.aar) — see README.md.
set(CARDBOARD_LIB ${CMAKE_CURRENT_SOURCE_DIR}/jni/${ANDROID_ABI}/libGfxPluginCardboard.so)

add_library(cardboard_jni SHARED
    cardboard_jni.cc
    cardboard_renderer.cc
    sphere_mesh.cc
    util.cc)

target_include_directories(cardboard_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(cardboard_jni
    ${ANDROID_LIB}
    ${GLESv2_LIB}
    ${GLESv3_LIB}
    ${EGL_LIB}
    ${LOG_LIB}
    ${CARDBOARD_LIB})

# 16 KB page alignment for Android 15+ devices (mirrors the SDK sample).
target_link_options(cardboard_jni PRIVATE "-Wl,-z,max-page-size=16384")
