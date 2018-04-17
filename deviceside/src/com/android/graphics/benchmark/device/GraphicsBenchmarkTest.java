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

package com.android.graphics.benchmark.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.android.graphics.benchmark.ApkInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;

@RunWith(Parameterized.class)
public class GraphicsBenchmarkTest {
    public static final String INTENT_ACTION = "com.android.graphics.benchmark.START";

    private static final String TAG = "GraphicsBenchmarkTest";

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        for (ApkInfo apk : ApkInfo.values()) {
            params.add(new Object[] { apk.name(), apk });
        }
        return params;
    }

    @Parameter(value = 0)
    public String apkName;

    @Parameter(value = 1)
    public ApkInfo apk;

    private Handler mHandler;

    @Test public void run()
            throws IntentFilter.MalformedMimeTypeException {
        startApp(apk);
    }

    private void startApp(ApkInfo app) throws IntentFilter.MalformedMimeTypeException {
        Looper.prepare();
        mHandler = new Handler();

        registerReceiver();
        Log.d(TAG, "Launching " + app.getPackageName());

        // TODO: Need to support passing arguments to intents.
        Intent intent =
                InstrumentationRegistry.getContext().getPackageManager()
                    .getLaunchIntentForPackage(app.getPackageName());
        InstrumentationRegistry.getContext().startActivity(intent);
        Handler handler = new Handler();
        handler.postDelayed(() -> handler.getLooper().quit(), 15000);
        Looper.loop();
    }

    private void registerReceiver() throws IntentFilter.MalformedMimeTypeException {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received intent");
                mHandler.getLooper().quit();
            }
        };
        IntentFilter intentFilter = new IntentFilter(INTENT_ACTION, "text/plain");
        InstrumentationRegistry.getContext().registerReceiver(br, intentFilter);
    }
}
