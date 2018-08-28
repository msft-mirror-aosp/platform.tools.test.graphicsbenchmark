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

gamecore_dist_host_jar := GameQualificationHelperHost GameQualificationHost
gamecore_dist_host_jar_files := $(foreach m, $(gamecore_dist_host_jar), $(HOST_OUT_JAVA_LIBRARIES)/$(m).jar)

gamecore_dist_test_apk := GameQualificationDevice GameQualificationSampleApp GameQualificationJavaTestCases
gamecore_dist_test_apk_files := $(foreach m, $(gamecore_dist_test_apk), $(TARGET_OUT_DATA_APPS)/$(m)/$(m).apk)

gamecore_dist_intermediates := $(call intermediates-dir-for,PACKAGING,gamecore_dist,HOST,COMMON)
gamecore_dist_zip := $(gamecore_dist_intermediates)/gamecore.zip

tradefed_files := \
    $(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar \
    $(HOST_OUT_JAVA_LIBRARIES)/tools-common-prebuilt.jar \
    $(BUILD_OUT_EXECUTABLES)/tradefed.sh \
    $(BUILD_OUT_EXECUTABLES)/tradefed_win.bat  \
    $(BUILD_OUT_EXECUTABLES)/script_help.sh \

gamecore_dist_files := \
    $(LOCAL_PATH)/AndroidTest.xml \
    $(gamecore_dist_host_jar_files) \
    $(gamecore_dist_test_apk_files) \
    $(tradefed_files)

$(gamecore_dist_zip) : $(gamecore_dist_files)
	@echo "Package: $@"
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)
	$(hide) cp -f $^ $(dir $@)
	$(hide) echo $(BUILD_NUMBER_FROM_FILE) > $(dir $@)/version.txt
	$(hide) cd $(dir $@) && zip -q $(notdir $@) $(notdir $^) version.txt

.PHONY: gamecore
gamecore: $(gamecore_dist_host_jar) $(gamecore_dist_test_apk)

.PHONY: gamecore-test
gamecore-test: GameQualificationHostTest GameQualificationHelperTest

.PHONY: gamecore-all
gamecore-all: gamecore gamecore-test

$(call dist-for-goals, gamecore, $(gamecore_dist_zip))

include $(call all-makefiles-under,$(LOCAL_PATH))
