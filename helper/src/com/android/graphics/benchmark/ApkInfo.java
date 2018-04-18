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

package com.android.graphics.benchmark;

public class ApkInfo {
    public static final String APK_LIST_LOCATION = "/sdcard/benchmark/apk-info.xml";

    private String mName;
    private String mFileName;
    private String mPackageName;

    public ApkInfo(String name, String fileName, String packageName) {
        this.mName = name;
        this.mFileName = fileName;
        this.mPackageName = packageName;
    }

    public String getName() {
        return mName;
    }

    public String getFileName() {
        return mFileName;
    }

    public String getPackageName() {
        return mPackageName;
    }
}
