cmake_minimum_required(VERSION 2.4)
project(DefaultSamples)

# Common file parsers
file(GLOB FILE_PARSER_CPP_FILES
     "${PROJECT_SOURCE_DIR}/../common/file_parser/helper_h264_parser.cpp"
     "${PROJECT_SOURCE_DIR}/../common/file_parser/helper_aac_parser.cpp")

# Build sample_send_yuv_pcm
file(GLOB SAMPLE_SEND_YUV_PCM_VIA_RTMP_CPP_FILES
     "${PROJECT_SOURCE_DIR}/sample_send_yuv_pcm_via_rtmp.cpp"
     "${PROJECT_SOURCE_DIR}/../common/*.cpp")
add_executable(sample_send_yuv_pcm_via_rtmp ${SAMPLE_SEND_YUV_PCM_VIA_RTMP_CPP_FILES})

file(GLOB SAMPLE_SEND
     "${PROJECT_SOURCE_DIR}/sample_receive_from_rtn_and_send_rtmp.cpp"
     "${PROJECT_SOURCE_DIR}/../common/*.cpp")
add_executable(sample_receive_from_rtn_and_send_rtmp ${SAMPLE_SEND})
