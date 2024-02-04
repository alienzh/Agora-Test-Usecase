cmake_minimum_required(VERSION 2.4)
project(AgoraRtcSdkRtmpCdnDemo)

SET(CMAKE_BUILD_TYPE "Debug")
SET(CMAKE_CXX_FLAGS_DEBUG "$ENV{CXXFLAGS} -O0 -Wall -g -ggdb")
SET(CMAKE_CXX_FLAGS_RELEASE "$ENV{CXXFLAGS} -O3 -Wall")

set(AGORA_MEDIA_SDK_DIR ${CMAKE_SOURCE_DIR}/../agora_sdk)
set(AGORA_MEDIA_SDK_HEADER_DIR ${CMAKE_SOURCE_DIR}/../agora_sdk)
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/../out/)
set(LIB_THREAD "pthread" "dl" "rt" "m")

# include header path
include_directories(
    "${CMAKE_SOURCE_DIR}"
    "${AGORA_MEDIA_SDK_HEADER_DIR}/include"
    "${PROJECT_SOURCE_DIR}/../example"
)

find_library(LIB_SDK NAMES agora_rtc_sdk agora-rtc-sdk agora-rtsa-sdk PATHS ${CMAKE_SOURCE_DIR}/../agora_sdk/ NO_DEFAULT_PATH REQUIRED)

# Link common libs
link_directories(${AGORA_MEDIA_SDK_DIR})
link_libraries(${LIB_SDK} ${LIB_THREAD})

# Build demo
file(GLOB DEMO_CPP_FILES
    "${PROJECT_SOURCE_DIR}/rtmp_cdn_demo.cpp"
    "${PROJECT_SOURCE_DIR}/../example/common/*.cpp")

add_executable(rtmp_cdn_demo ${DEMO_CPP_FILES})

