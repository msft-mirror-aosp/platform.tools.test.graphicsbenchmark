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

# Build all sub-directories
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

gamecore_dist_host_jar := GameQualificationHelperHost GameQualificationHost truth-prebuilt
gamecore_dist_host_jar_files := $(foreach m, $(gamecore_dist_host_jar), $(HOST_OUT_JAVA_LIBRARIES)/$(m).jar)

gamecore_dist_test_exe := GameQualificationNativeTestCases
gamecore_dist_test_exe_files := $(foreach m, $(gamecore_dist_test_exe), $(TARGET_OUT_TESTCASES)/$(m)/$(TARGET_ARCH)/$(m))

gamecore_dist_test_apk := GameQualificationDevice GameQualificationSampleApp GameQualificationJavaTestCases GameQualificationAllocstress
gamecore_dist_test_apk_files := $(foreach m, $(gamecore_dist_test_apk), $(TARGET_OUT_DATA_APPS)/$(m)/$(m).apk)

gamecore_dist_intermediates := $(call intermediates-dir-for,PACKAGING,gamecore_dist,HOST,COMMON)
gamecore_dist_dir := $(gamecore_dist_intermediates)/gamecore
gamecore_dist_zip := $(gamecore_dist_intermediates)/gamecore.zip

tradefed_files := \
    $(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar \
    $(HOST_OUT_JAVA_LIBRARIES)/tools-common-prebuilt.jar \
    $(BUILD_OUT_EXECUTABLES)/tradefed.sh \
    $(BUILD_OUT_EXECUTABLES)/tradefed_win.bat  \
    $(BUILD_OUT_EXECUTABLES)/script_help.sh \

config_files := \
    $(LOCAL_PATH)/AndroidTest.xml \
    $(LOCAL_PATH)/dist/run_gamecore.sh \
    $(LOCAL_PATH)/dist/README

gamecore_dist_files := \
    $(config_files) \
    $(gamecore_dist_host_jar_files) \
    $(gamecore_dist_test_apk_files) \
    $(gamecore_dist_test_exe_files) \
    $(tradefed_files)

# Copy files into appropriate directories and create gamecore.zip
$(gamecore_dist_zip) : $(gamecore_dist_files)
	@echo "Package: $@"
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)/gamecore
	$(hide) mkdir -p $(dir $@)/gamecore/bin
	$(hide) mkdir -p $(dir $@)/gamecore/testcases/$(TARGET_ARCH)
	$(hide) cp -f $(tradefed_files) $(dir $@)/gamecore/bin
	$(hide) cp -f $(gamecore_dist_host_jar_files) $(dir $@)/gamecore/bin/
	$(hide) cp -f $(gamecore_dist_test_apk_files) $(dir $@)/gamecore/testcases/
	$(hide) cp -f $(gamecore_dist_test_exe_files) $(dir $@)/gamecore/testcases/$(TARGET_ARCH)/
	$(hide) cp -f $(config_files) $(dir $@)/gamecore
	$(hide) echo $(BUILD_NUMBER_FROM_FILE) > $(dir $@)/gamecore/version.txt
	$(hide) cd $(dir $@) && zip -q -r $(notdir $@) gamecore

.PHONY: gamecore
gamecore: $(gamecore_dist_host_jar) $(gamecore_dist_test_apk)

.PHONY: gamecore-test
gamecore-test: GameQualificationHostTest GameQualificationHelperTest

.PHONY: gamecore-all
gamecore-all: gamecore gamecore-test

$(call dist-for-goals, gamecore, $(gamecore_dist_zip))

include $(call all-makefiles-under,$(LOCAL_PATH))
