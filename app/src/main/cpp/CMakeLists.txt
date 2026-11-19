cmake_minimum_required(VERSION 3.22.1)
project(miragelens)

# Prebuilt Cactus engine (libc++ based; the NDK's default STL is libc++ too).
add_library(cactus STATIC IMPORTED)
set_target_properties(cactus PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libcactus.a)

add_library(miragelens SHARED native-lib.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(miragelens cactus ${log-lib} ${android-lib})
