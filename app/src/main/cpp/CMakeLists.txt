cmake_minimum_required(VERSION 3.22.1)
project("hearlink_audio")

find_package(oboe REQUIRED CONFIG)

add_library(
    hearlink_audio
    SHARED
    jni_bridge.cpp
    AudioEngine.cpp
    DspPipeline.cpp
    LoopbackDetector.cpp
)

target_compile_features(hearlink_audio PRIVATE cxx_std_17)
target_compile_options(hearlink_audio PRIVATE -O3 -Wall -Wextra -Wno-unused-parameter)

target_link_libraries(
    hearlink_audio
    oboe::oboe
    log
    android
)
