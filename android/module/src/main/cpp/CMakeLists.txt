cmake_minimum_required(VERSION 3.22)
project(cyclone_cloak CXX)

# The embedded-assets translation unit is generated per-ABI by the Gradle
# embedHookAssets task before CMake configure runs, so no Python interpreter
# is needed inside the NDK toolchain.
set(EMBEDDED_SRC ${CMAKE_CURRENT_SOURCE_DIR}/embedded/${ANDROID_ABI}/embedded_assets.cpp)

if(NOT EXISTS ${EMBEDDED_SRC})
    message(FATAL_ERROR "embedded_assets.cpp not found for ${ANDROID_ABI}; run :module:embedHookAssets first")
endif()

add_library(cloak SHARED cloak.cpp ${EMBEDDED_SRC})

find_library(log-lib log)
target_link_libraries(cloak ${log-lib})
target_compile_features(cloak PRIVATE cxx_std_17)