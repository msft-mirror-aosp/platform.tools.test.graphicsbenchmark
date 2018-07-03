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

import static com.android.game.qualification.metric.MetricSummary.TimeType.PRESENT;
import static com.android.game.qualification.metric.MetricSummary.TimeType.READY;

import com.android.annotations.Nullable;
import com.android.game.qualification.ApkInfo;
import com.android.game.qualification.CertificationRequirements;
import com.android.game.qualification.proto.ResultDataProto;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import com.google.common.base.Preconditions;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A {@link com.android.tradefed.device.metric.ScheduledDeviceMetricCollector} to collect graphics
 * benchmarking stats at regular intervals.
 */
public class GameQualificationMetricCollector extends BaseDeviceMetricCollector {
    private long mLatestSeen = 0;
    @Nullable
    private ApkInfo mTestApk;
    @Nullable
    private CertificationRequirements mCertificationRequirements;
    private ResultDataProto.Result mDeviceResultData;
    private long mVSyncPeriod = 0;
    private ArrayList<GameQualificationMetric> mElapsedTimes;
    private ITestDevice mDevice;
    private boolean mAppStarted;
    private boolean mAppTerminated;
    private File mRawFile;
    private boolean mEnabled;
    private Pattern mLayerPattern;
    private String mTestLayer;

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
    private long mIntervalMs = 1 * 1000L;

    private Timer mTimer;

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

    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    public boolean isAppStarted() {
        synchronized(this) {
            return mAppStarted;
        }
    }

    public boolean isAppTerminated() {
        synchronized(this) {
            return mAppTerminated;
        }
    }

    public void enable() {
        mEnabled = true;
    }

    public void disable() {
        mEnabled = false;
    }

    @Override
    public final void onTestStart(DeviceMetricData runData) {
        if (!mEnabled) {
            // GameQualificationMetricCollector is only enabled by
            // GameQualificationHostsideController.
            return;
        }
        Preconditions.checkState(mTestApk != null);

        CLog.v("Test run started on device %s.", mDevice);

        try {
            mRawFile = File.createTempFile("GameQualification_RAW_TIMES", ".txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        synchronized(this) {
            mElapsedTimes = new ArrayList<>();
            mLatestSeen = 0;
            mAppStarted = false;
            mAppTerminated = false;

            try {
                mLayerPattern = Pattern.compile(mTestApk.getLayerName());
            } catch (PatternSyntaxException e) {
                // TODO: Hostside controller should properly report the error
                CLog.e(e);
                mAppStarted = false;
                return;
            }
        }



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
                            CLog.e("Interrupted Exception thrown from task: %s", e);
                        } catch (Exception e) {
                            mTimer.cancel();
                            Thread.currentThread().interrupt();
                            CLog.e("Test app '%s' terminated before data collection was completed.", mTestApk.getName());
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
    public final void onTestEnd(DeviceMetricData runData, Map<String, Metric> currentRunMetrics) {
        if (!mEnabled) {
            return;
        }

        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
        }
        onEnd(runData);
        CLog.d("onTestRunEnd");
    }


    /**
     * Task periodically & asynchronously run during the test running.
     *
     * @param runData the {@link DeviceMetricData} where to put metrics.
     * @throws InterruptedException
     */
    private void collect(DeviceMetricData runData) throws InterruptedException {
        synchronized(this) {
            try {

                if (!mAppStarted) {
                    String listCmd = "dumpsys SurfaceFlinger --list";
                    String[] layerList = mDevice.executeShellCommand(listCmd).split("\n");

                    for (int i = 0; i < layerList.length; i++) {
                        Matcher m = mLayerPattern.matcher(layerList[i]);
                        if (m.matches()) {
                            mTestLayer = layerList[i];
                        }
                    }
                }

                CLog.d("Collecting benchmark stats for layer: %s", mTestLayer);

                String cmd = "dumpsys SurfaceFlinger --latency \"" + mTestLayer+ "\"";
                String[] raw = mDevice.executeShellCommand(cmd).split("\n");

                if (raw.length == 1) {
                    if (mAppStarted) {
                        mAppTerminated = true;
                        throw new RuntimeException();
                    }
                    else
                        return;
                }

                if (!mAppStarted) {
                    mVSyncPeriod = Long.parseLong(raw[0]);
                    mAppStarted = true;
                }

                try (BufferedWriter outputFile = new BufferedWriter(new FileWriter(mRawFile, true))) {
                    outputFile.write("Vsync: " + raw[0] + "\n");
                    outputFile.write("Latest Seen: " + mLatestSeen + "\n");

                    outputFile.write(String.format("%20s", "Desired Present Time") + "\t");
                    outputFile.write(String.format("%20s", "Actual Present Time") + "\t");
                    outputFile.write(String.format("%20s", "Frame Ready Time") + "\n");

                    boolean overlap = false;
                    for (int i = 1; i < raw.length; i++) {
                        String[] parts = raw[i].split("\t");

                        if (parts.length == 3) {
                            if (sample(Long.parseLong(parts[2]), Long.parseLong(parts[1]))) {
                                overlap = true;
                            }
                        }

                        outputFile.write(String.format("%20d", Long.parseLong(parts[0])) + "\t");
                        outputFile.write(String.format("%20d", Long.parseLong(parts[1])) + "\t");
                        outputFile.write(String.format("%20d", Long.parseLong(parts[2])) + "\n");
                    }

                    if (!overlap) {
                        CLog.e("No overlap with previous poll, we missed some frames!"); // FIND SOMETHING BETTER
                    }

                    outputFile.write("\n\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }



            } catch (DeviceNotAvailableException | NullPointerException e) {
                CLog.e(e);
            }
        }
    }

    private boolean sample(long readyTimeStamp, long presentTimeStamp) {
        if (presentTimeStamp == Long.MAX_VALUE) {
            return true;
        }
        else if (presentTimeStamp < mLatestSeen) {
            return false;
        }
        else if (presentTimeStamp == mLatestSeen) {
            return true;
        }
        else {
            mElapsedTimes.add(new GameQualificationMetric(presentTimeStamp, readyTimeStamp));
            mLatestSeen = presentTimeStamp;
            return false;
        }
    }


    private void onStart(DeviceMetricData runData) {}

    private void processTimestampsSlice(
            MetricSummary.Builder summary,
            int runIndex,
            long startTimestamp,
            long endTimestamp,
            BufferedWriter outputFile) throws IOException {
        outputFile.write("Started run " + runIndex + " at: " + startTimestamp + " ns\n");

        outputFile.write("Present Time\tFrame Ready Time\n");

        long prevPresentTime = 0, prevReadyTime = 0;
        int numOfTimestamps = 0;

        List<Long> frameTimes = new ArrayList<>();

        summary.beginLoop();
        for(GameQualificationMetric metric : mElapsedTimes)
        {
            long presentTime = metric.getActualPresentTime();
            long readyTime = metric.getFrameReadyTime();

            if (presentTime < startTimestamp) {
                continue;
            }
            if (presentTime > endTimestamp) {
                break;
            }

            if (prevPresentTime == 0) {
                prevPresentTime = presentTime;
                prevReadyTime = readyTime;
                continue;
            }

            long presentTimeDiff = presentTime - prevPresentTime;
            prevPresentTime = presentTime;
            summary.addFrameTime(PRESENT, presentTimeDiff);

            long readyTimeDiff = readyTime - prevReadyTime;
            prevReadyTime = readyTime;
            summary.addFrameTime(READY, readyTimeDiff);

            numOfTimestamps++;

            outputFile.write(presentTimeDiff + " ns\t\t" + readyTimeDiff + " ns\n");
            frameTimes.add(presentTimeDiff);
        }
        summary.endLoop();

        // There's a fair amount of slop in the system wrt device timing vs host orchestration,
        // so it's possible that we'll receive an extra intent after we've stopped caring.
        if (numOfTimestamps == 0) {
            outputFile.write("No samples in period, assuming spurious intent.\n\n");
            return;
        }

        printHistogram(frameTimes, runIndex);
    }

    private void onEnd(DeviceMetricData runData) {
        synchronized(this) {
            if (mElapsedTimes.isEmpty()) {
                return;
            }
            try {
                try(InputStreamSource rawData = new FileInputStreamSource(mRawFile, true)) {
                        testLog("RAW-" + mTestApk.getName(), LogDataType.TEXT, rawData);
                }

                 mRawFile.delete();

                File tmpFile = File.createTempFile("GameQualification", ".txt");
                try (BufferedWriter outputFile = new BufferedWriter(new FileWriter(tmpFile))) {
                    if (mAppTerminated) {
                        outputFile.write("NOTE: THE APPLICATION WAS INTERRUPTED AT SOME POINT DURING THE TEST. RESULTS MAY BE INCOMPLETE.\n\n\n\n");
                    }

                    outputFile.write("VSync Period: " + mVSyncPeriod + "\n\n");

                    MetricSummary.Builder summaryBuilder =
                            new MetricSummary.Builder(mCertificationRequirements, mVSyncPeriod);

                    long startTime = 0L;
                    int runIndex = 0;

                    // Calculate load time.
                    long appLaunchedTime = 0;
                    for (ResultDataProto.Event e : mDeviceResultData.getEventsList()) {
                        if (e.getType() == ResultDataProto.Event.Type.APP_LAUNCH) {
                             appLaunchedTime = e.getTimestamp();
                             continue;
                        }
                        // Get the first START_LOOP.  Assume START_LOOP is in chronological order
                        // and comes after APP_LAUNCH.
                        if (e.getType() == ResultDataProto.Event.Type.START_LOOP) {
                            summaryBuilder.setLoadTimeMs(e.getTimestamp() - appLaunchedTime);
                            break;
                        }
                    }
                    for (ResultDataProto.Event e : mDeviceResultData.getEventsList()) {
                        if (e.getType() != ResultDataProto.Event.Type.START_LOOP) {
                            continue;
                        }

                        long endTime = e.getTimestamp() * 1000000;  /* ms to ns */

                        if (startTime != 0) {
                            processTimestampsSlice(summaryBuilder, runIndex++, startTime, endTime, outputFile);
                        }
                        startTime = endTime;
                    }

                    processTimestampsSlice(
                            summaryBuilder,
                            runIndex,
                            startTime,
                            mElapsedTimes.get(mElapsedTimes.size() - 1).getActualPresentTime(),
                            outputFile);

                    MetricSummary summary = summaryBuilder.build();
                    summary.addToMetricData(runData);
                    outputFile.write(summary.toString());
                    outputFile.flush();
                    try(InputStreamSource source = new FileInputStreamSource(tmpFile, true)) {
                        testLog("GameQualification-" + mTestApk.getName(), LogDataType.TEXT, source);
                    }
                }
                tmpFile.delete();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void printHistogram(Collection<Long> frameTimes, int runIndex) {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Histogram histogram =
                    new Histogram(frameTimes, mVSyncPeriod / 30L, null, 5 * mVSyncPeriod);
            histogram.plotAscii(output, 100);
            try(InputStreamSource source = new ByteArrayInputStreamSource(output.toByteArray())) {
                testLog(
                        "GameQualification-histogram-" + mTestApk.getName() + "-run" + runIndex,
                        LogDataType.TEXT,
                        source);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
