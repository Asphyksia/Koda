LOCAL_PATH := $(call my-dir)

# 1. libtermux-bootstrap — extracts bootstrap zip
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
include $(BUILD_SHARED_LIBRARY)

# 2. libkoda-process — JNI fork/execvp for subprocess management
include $(CLEAR_VARS)
LOCAL_MODULE := libkoda-process
LOCAL_SRC_FILES := koda-process.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
