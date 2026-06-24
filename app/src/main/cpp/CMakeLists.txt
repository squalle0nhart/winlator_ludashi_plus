cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(OpenXR-SDK)
add_subdirectory(patchelf)
add_subdirectory(adrenotools)

find_program(GLSLC
        NAMES glslc
        HINTS
        "$ENV{ANDROID_NDK}/shader-tools/darwin-x86_64"
        "$ENV{ANDROID_NDK_HOME}/shader-tools/darwin-x86_64"
        "$ENV{ANDROID_SDK_ROOT}/ndk/29.0.14206865/shader-tools/darwin-x86_64"
        "$ENV{HOME}/Library/Android/sdk/ndk/29.0.14206865/shader-tools/darwin-x86_64"
)

if(NOT GLSLC)
    message(FATAL_ERROR "glslc not found. Install Android NDK shader tools or expose them via ANDROID_NDK/ANDROID_SDK_ROOT.")
endif()

add_library(winlator SHARED
        xr/engine.c
        xr/framebuffer.c
        xr/input.c
        winlator/process_helper.c
        xr/main.c
        xr/math.c
        xr/renderer.c
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/vulkan.c
)

target_compile_options(winlator PRIVATE -Wall -Wextra)

target_link_libraries(winlator
        log
        android
        jnigraphics
        openxr_loader
        aaudio
        EGL
        GLESv2
        GLESv3
        adrenotools
)

add_library(fakeinput SHARED
        winlator/fakeinput.cpp
)

target_compile_options(fakeinput PRIVATE -Wall -Wextra -fvisibility=hidden)

target_link_libraries(fakeinput
        log
        dl
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
            ${GLSLC} vert window_vert_code
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.vert
            ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.vert
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
    COMMENT "Compiling window.vert → window_vert.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
            ${GLSLC} frag window_frag_code
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.frag
            ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.frag
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
    COMMENT "Compiling window.frag → window_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_sgsr_frag.h
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
            ${GLSLC} frag window_sgsr_frag_code
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window_sgsr.frag
            ${CMAKE_CURRENT_BINARY_DIR}/window_sgsr_frag.h
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window_sgsr.frag
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
    COMMENT "Compiling window_sgsr.frag → window_sgsr_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_stretch_frag.h
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
            ${GLSLC} frag window_stretch_frag_code
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window_stretch.frag
            ${CMAKE_CURRENT_BINARY_DIR}/window_stretch_frag.h
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window_stretch.frag
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
    COMMENT "Compiling window_stretch.frag → window_stretch_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_postfx_frag.h
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
            ${GLSLC} frag window_postfx_frag_code
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window_postfx.frag
            ${CMAKE_CURRENT_BINARY_DIR}/window_postfx_frag.h
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window_postfx.frag
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/gen_shader_header.sh
    COMMENT "Compiling window_postfx.frag → window_postfx_frag.h"
)

include_directories(${CMAKE_CURRENT_BINARY_DIR})

add_library(vulkan_renderer SHARED
        winlator/vulkan_jni.cpp
        winlator/VulkanRendererContext.cpp
        winlator/VulkanRendererScanout.cpp

        ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_sgsr_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_stretch_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_postfx_frag.h
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(vulkan_renderer
        log
        android
        vulkan
        adrenotools
        dl
        atomic
)
