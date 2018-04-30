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

package com.android.graphics.benchmark.metric;

import com.android.graphics.benchmark.ApkInfo;

import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.graphics.benchmark.ApkInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.config.Option;

import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

/** A {@link ScheduledDeviceMetricCollector} to collect graphics benchmarking stats at regular intervals. */
public class GraphicsBenchmarkMetricCollector extends BaseDeviceMetricCollector {

    private long mLatestSeen = 0;
    private static ApkInfo mTestApk;
    private long mVSyncPeriod = 0;
    private ArrayList<Long> mElapsedTimes;
    private ITestDevice mDevice;
    private boolean mFirstRun = true;

    // TODO: Investigate interaction with sharding support
    public static void setAppLayerName(ApkInfo apk) {
        mTestApk = apk;
    }

    @Option(
        name = "fixed-schedule-rate",
        description = "Schedule the timetask as a fixed schedule rate"
    )
    private boolean mFixedScheduleRate = false;

    @Option(
        name = "interval",
        description = "the interval between two tasks being scheduled",
        isTimeVal = true
    )
    private long mIntervalMs = 1 * 1000l;

    private Timer mTimer;

    @Override
    public final void onTestRunStart(DeviceMetricData runData) {
        CLog.e("Attempt to get device from onTestRunStart");
        mDevice = getDevices().get(0);
        CLog.e("Device : " + mDevice);

        mElapsedTimes = new ArrayList<Long>();

        CLog.e("starting");
        onStart(runData);
        mTimer = new Timer();
        TimerTask timerTask =
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            collect(runData);
                        } catch (InterruptedException e) {
                            mTimer.cancel();
                            Thread.currentThread().interrupt();
                            CLog.e("Interrupted exception thrown from task:");
                            CLog.e(e);
                        }
                    }
                };

        if (mFixedScheduleRate) {
            mTimer.scheduleAtFixedRate(timerTask, 0, mIntervalMs);
        } else {
            mTimer.schedule(timerTask, 0, mIntervalMs);
        }
    }

    @Override
    public final void onTestRunEnd(DeviceMetricData runData) {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
        }
        onEnd(runData);
        CLog.d("finished");
    }


    /**
     * Task periodically & asynchronously run during the test running.
     *
     * @param runData the {@link DeviceMetricData} where to put metrics.
     * @throws InterruptedException
     */
    private void collect(DeviceMetricData runData) throws InterruptedException {
        try {
            CLog.e("Running benchmarking stats...");

            String cmd;
            String[] layerList;

            if (mTestApk == null) {
                CLog.e("No test apk provided!");
                return;
            }

            CLog.e("Target Layer: " + mTestApk.getLayerName());

            boolean firstLoop = true;
            cmd = "dumpsys SurfaceFlinger --latency \"" + mTestApk.getLayerName()+ "\"";
            String[] raw = mDevice.executeShellCommand(cmd).split("\n");

            if (firstLoop) {
                mVSyncPeriod = Long.parseLong(raw[0]);
                firstLoop = false;
            }

            boolean overlap = false;
            for (int i = 1; i < raw.length; i++) {
                String[] parts = raw[i].split("\t");

                if (parts.length == 3) {
                    if (sample(Long.parseLong(parts[2]))) {
                        overlap = true;
                    }
                }
            }

            if (!overlap)
                CLog.e("No overlap with previous poll, we missed some frames!"); // FIND SOMETHING BETTER

        } catch (DeviceNotAvailableException | NullPointerException e) {
            CLog.e(e);
        }
    }

    private boolean sample(long timeStamp) {
        if (timeStamp < mLatestSeen) {
            return false;
        }
        else if (timeStamp == mLatestSeen) {
            return true;
        }
        else {
            mElapsedTimes.add(timeStamp - mLatestSeen);
            mLatestSeen = timeStamp;
            return false;
        }
    }


    private void onStart(DeviceMetricData runData) {}

    private void onEnd(DeviceMetricData runData) {
        double minFPS = Double.MAX_VALUE, maxFPS = 0.0, avgFPS = 0.0;

        // TODO: Find a way to send the results to the same directory as the inv. log files
        try (BufferedWriter outputFile = new BufferedWriter(new FileWriter("/tmp/0/graphics-benchmark/out.txt", !mFirstRun))) {
            outputFile.write("VSync Period: " + mVSyncPeriod + "\n");

            outputFile.write("Times:\n");
            for(Long time : mElapsedTimes)
            {
                double currentFPS = 1.0e9/time;
                minFPS = (currentFPS < minFPS ? currentFPS : minFPS);
                maxFPS = (currentFPS > maxFPS ? currentFPS : maxFPS);
                avgFPS += currentFPS;

                outputFile.write(currentFPS + "\n");
            }

            outputFile.write("\nSTATS\n");

            avgFPS = avgFPS / mElapsedTimes.size();

            outputFile.write("min FPS = " + minFPS + "\n");
            outputFile.write("max FPS = " + maxFPS + "\n");
            outputFile.write("avg FPS = " + avgFPS + "\n");

            outputFile.write("\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mFirstRun = false;
    }
}