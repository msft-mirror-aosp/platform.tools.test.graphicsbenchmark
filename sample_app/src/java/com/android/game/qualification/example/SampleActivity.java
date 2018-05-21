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
package com.android.game.qualification.example;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;

public class SampleActivity extends Activity {
    private static final long LOOP_PERIOD_MS = 5 * 1000L;

    private GLSurfaceView mGLView;
    private Handler mHandler;
    private Runnable mTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity.
        mGLView = new MyGLSurfaceView(this);
        setContentView(mGLView);

        // Loop every 5s.
        mHandler = new Handler();
        mTask = new Runnable() {
            @Override
            public void run() {
                broadcastIntent();
                mHandler.postDelayed(this, LOOP_PERIOD_MS);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.postDelayed(mTask, LOOP_PERIOD_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTask);
    }

    public native void broadcastIntent();

    static {
        System.loadLibrary("sample");
    }
}
