cmake_minimum_required(VERSION 3.18.1)

project("ndkcube")

# Find Android NDK libraries
find_library(log-lib log)
find_library(gles3-lib GLESv3)

# Define our native library
add_library(ndkcube SHARED cube-renderer.cpp)

# Link C++ libraries
target_link_libraries(ndkcube
        ${log-lib}
        ${gles3-lib}
)
