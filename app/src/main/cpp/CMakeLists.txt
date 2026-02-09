cmake_minimum_required(VERSION 3.13)
project("modernrdp-native" LANGUAGES C VERSION 0.1.0)

# Path to pre-built FreeRDP libraries and headers per ABI
set(JNILIBS_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI}")

# ---- Import pre-built FreeRDP shared libraries ----

add_library(freerdp3 SHARED IMPORTED)
set_target_properties(freerdp3 PROPERTIES
    IMPORTED_LOCATION "${JNILIBS_DIR}/libfreerdp3.so"
)

add_library(freerdp-client3 SHARED IMPORTED)
set_target_properties(freerdp-client3 PROPERTIES
    IMPORTED_LOCATION "${JNILIBS_DIR}/libfreerdp-client3.so"
)

add_library(winpr3 SHARED IMPORTED)
set_target_properties(winpr3 PROPERTIES
    IMPORTED_LOCATION "${JNILIBS_DIR}/libwinpr3.so"
)

# ---- Our JNI bridge library ----

add_library(modernrdp-native SHARED
    modernrdp_jni.c
    modernrdp_jni.h
    modernrdp_event.c
    modernrdp_callback.c
    modernrdp_callback.h
)

target_include_directories(modernrdp-native PRIVATE
    "${JNILIBS_DIR}/include/freerdp3"
    "${JNILIBS_DIR}/include/winpr3"
    "${JNILIBS_DIR}/include/openssl"
)

target_compile_options(modernrdp-native PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -gdwarf-3
)

target_link_libraries(modernrdp-native
    freerdp3
    freerdp-client3
    winpr3
    log             # Android logging (android/log.h)
    dl              # Dynamic loading
    jnigraphics     # Android bitmap access (android/bitmap.h)
)
