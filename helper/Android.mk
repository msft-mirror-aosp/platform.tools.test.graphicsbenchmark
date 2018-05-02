# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Make a mock compatibility suite to test
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)


LOCAL_SRC_FILES := \
	$(call all-java-files-under, src)\
	$(call all-proto-files-under, proto)
LOCAL_SDK_VERSION := current
LOCAL_MODULE := graphicsbenchmarkhelper
LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	$(call all-java-files-under, src) \
	$(call all-proto-files-under, proto)
LOCAL_MODULE := graphicsbenchmarkhelper-host
LOCAL_MODULE_TAGS := optional
LOCAL_COMPATIBILITY_SUITE := general-tests

include $(BUILD_HOST_JAVA_LIBRARY)


# Test
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, test)
LOCAL_MODULE := graphicsbenchmarkhelper-test
LOCAL_MODULE_TAGS := tests
LOCAL_COMPATIBILITY_SUITE := general-tests
LOCAL_JAVA_LIBRARIES := graphicsbenchmarkhelper-host junit-host

include $(BUILD_HOST_JAVA_LIBRARY)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
