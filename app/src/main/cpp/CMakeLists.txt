cmake_minimum_required(VERSION 3.22.1)
project(pcmprocessor_jni)

add_library(
    pcmprocessor_jni
    SHARED
    audio_processor_jni.cpp
)

find_library(log-lib log)

target_link_libraries(
    pcmprocessor_jni
    ${log-lib}
)

