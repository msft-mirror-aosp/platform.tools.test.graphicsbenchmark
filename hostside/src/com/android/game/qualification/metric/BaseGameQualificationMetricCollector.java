/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.game.qualification.metric;

import com.android.annotations.Nullable;
import com.android.game.qualification.ApkInfo;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.ITestDevice;
import com.android.game.qualification.CertificationRequirements;
import com.android.game.qualification.proto.ResultDataProto;

public abstract class BaseGameQualificationMetricCollector extends BaseDeviceMetricCollector {
    @Nullable
    protected ApkInfo mTestApk;
    @Nullable
    protected CertificationRequirements mCertificationRequirements;
    protected ResultDataProto.Result mDeviceResultData;
    protected ITestDevice mDevice;
    private boolean mEnabled;
    private boolean mHasError;
    private String mErrorMessage;

    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    public void setApkInfo(ApkInfo apk) {
        synchronized(this) {
            mTestApk = apk;
        }
    }

    public void setCertificationRequirements(@Nullable CertificationRequirements requirements) {
        synchronized(this) {
            mCertificationRequirements = requirements;
        }
    }

    public void setDeviceResultData(ResultDataProto.Result resultData) {
        mDeviceResultData = resultData;
    }

    public boolean hasError() {
        synchronized(this) {
            return mHasError;
        }
    }

    protected void setHasError(boolean hasError) {
        synchronized(this) {
            mHasError = hasError;
        }
    }

    public String getErrorMessage() {
        synchronized (this) {
            return mErrorMessage;
        }
    }

    protected void setErrorMessage(String msg) {
        mErrorMessage = msg;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void enable() {
        mEnabled = true;
    }

    public void disable() {
        mEnabled = false;
    }
}