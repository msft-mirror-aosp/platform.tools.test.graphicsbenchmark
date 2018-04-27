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

import java.util.Collection;
import java.util.List;

public class ApkInfo {
    public static final String APK_LIST_LOCATION = "/sdcard/benchmark/apk-info.xml";

    //TODO: support non-String args.
    public static class Argument {
        private String mKey;
        private String mValue;

        public Argument(String key, String value) {
            mKey = key;
            mValue = value;
        }

        public String getKey() {
            return mKey;
        }

        public String getValue() {
            return mValue;
        }
    }

    private String mName;
    private String mFileName;
    private String mPackageName;
    private List<Argument> mArgs;

    public ApkInfo(String name, String fileName, String packageName, List<Argument> args) {
        this.mName = name;
        this.mFileName = fileName;
        this.mPackageName = packageName;
        this.mArgs = args;
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

    public List<Argument> getArgs() {
        return mArgs;
    }
}
