cmake_minimum_required(VERSION 3.11)
set(CMAKE_CXX_STANDARD 11)
include(FetchContent REQUIRED)

# show fetchcontent progress
set(FETCHCONTENT_QUIET OFF CACHE BOOL "Turn off FetchContent verbose output." FORCE)
set(LIBMINISYNCPP_ENABLE_LOGURU FALSE CACHE BOOL "" FORCE)
set(LIBMINISYNCPP_WITH_PYTHON FALSE CACHE BOOL "" FORCE)
set(LIBMINISYNCPP_BUILD_TESTS FALSE CACHE BOOL "" FORCE)
set(LIBMINISYNCPP_BUILD_DEMO FALSE CACHE BOOL "" FORCE)
### Fetch libminisyncpp
FetchContent_Declare(
            minisyncpp
            # download
            GIT_REPOSITORY "https://github.com/molguin92/MiniSynCPP.git"
            GIT_TAG "master" # or a specific release tag
            # ---
            )
FetchContent_GetProperties(minisyncpp)
if (NOT minisyncpp_POPULATED)
    FetchContent_Populate(minisyncpp)
    add_subdirectory(${minisyncpp_SOURCE_DIR})

    ### The project exports two variables for easy use:
    # LIBMINISYNCPP_HDR: the full path to the api header file minisyncp_api.h
    # LIBMINISYNCPP_INCLUDE: the full path to the library include directory

    include_directories(${LIBMINISYNCPP_INCLUDE})
endif ()
