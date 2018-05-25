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

import com.android.game.qualification.ApkInfo;
import com.android.game.qualification.proto.ResultDataProto;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Directionality;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

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

/** A {@link ScheduledDeviceMetricCollector} to collect graphics benchmarking stats at regular intervals. */
public class GameQualificationMetricCollector extends BaseDeviceMetricCollector {
    private long mLatestSeen = 0;
    private ApkInfo mTestApk;
    private ResultDataProto.Result mDeviceResultData;
    private long mVSyncPeriod = 0;
    private ArrayList<GameQualificationMetric> mElapsedTimes;
    private ITestDevice mDevice;
    private boolean mFirstLoop;
    private File mRawFile;

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
        mTestApk = apk;
    }

    public void setDeviceResultData(ResultDataProto.Result resultData) {
        mDeviceResultData = resultData;
    }

    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public final void onTestRunStart(DeviceMetricData runData) {
        CLog.v("Test run started on device %s.", mDevice);

        try {
            mRawFile = File.createTempFile("GameQualification_RAW_TIMES", ".txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mElapsedTimes = new ArrayList<>();
        mLatestSeen = 0;
        mFirstLoop = true;

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
                            CLog.e("Interrupted exception thrown from task: %s", e);
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
    public final void onTestRunEnd(DeviceMetricData runData, Map<String, Metric> currentRunMetrics) {
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
        try {

            if (mTestApk == null) {
                CLog.e("No test apk info provided.");
                return;
            }
            CLog.d("Collecting benchmark stats for layer: %s", mTestApk.getLayerName());

            String cmd = "dumpsys SurfaceFlinger --latency \"" + mTestApk.getLayerName()+ "\"";
            String[] raw = mDevice.executeShellCommand(cmd).split("\n");

            if (mFirstLoop) {
                if (raw.length == 1) {
                    // We didn't get any frame timestamp info.  Mostly likely because the app has
                    // not started yet.  Or the app layer name is wrong.
                    // TODO: figure out how to report it if the app layer name is wrong.
                    return;
                }
                mVSyncPeriod = Long.parseLong(raw[0]);
                mFirstLoop = false;
            }

            try (BufferedWriter outputFile = new BufferedWriter(new FileWriter(mRawFile, true))) {
                outputFile.write("Vsync: " + raw[0] + "\n");

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

    private void processTimestampsSlice(int runIndex, long startTimestamp, long endTimestamp, BufferedWriter outputFile, DeviceMetricData runData) throws IOException {
        MetricSummary presentTimeSummary = new MetricSummary();
        MetricSummary readyTimeSummary = new MetricSummary();

        outputFile.write("Started run " + runIndex + " at: " + startTimestamp + " ns\n");

        outputFile.write("Present Time\tFrame Ready Time\n");

        long prevPresentTime = 0, prevReadyTime = 0;
        int numOfTimestamps = 0;

        List<Long> frameTimes = new ArrayList<>();

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

            presentTimeSummary.processTimestamp(presentTimeDiff);



            long readyTimeDiff = readyTime - prevReadyTime;
            prevReadyTime = readyTime;

            readyTimeSummary.processTimestamp(readyTimeDiff);

            numOfTimestamps++;

            outputFile.write(presentTimeDiff + " ns\t\t" + readyTimeDiff + " ns\n");
            frameTimes.add(presentTimeDiff);
        }

        // There's a fair amount of slop in the system wrt device timing vs host orchestration,
        // so it's possible that we'll receive an extra intent after we've stopped caring.
        if (numOfTimestamps == 0) {
            outputFile.write("No samples in period, assuming spurious intent.\n\n");
            return;
        }

        outputFile.write("\nSTATS\n");

        presentTimeSummary.processAverages(numOfTimestamps);
        outputFile.write("Present Summary Statistics\n");
        outputFile.write(presentTimeSummary + "\n\n");

        readyTimeSummary.processAverages(numOfTimestamps);
        outputFile.write("Frame Ready Summary Statistics\n");
        outputFile.write(readyTimeSummary + "\n\n");

        runData.addMetric("run_" + runIndex + ".present_min_fps", getFpsMetric(presentTimeSummary.minFPS));
        runData.addMetric("run_" + runIndex + ".present_max_fps", getFpsMetric(presentTimeSummary.maxFPS));
        runData.addMetric("run_" + runIndex + ".present_fps", getFpsMetric(presentTimeSummary.avgFPS));

        runData.addMetric("run_" + runIndex + ".present_min_frametime", getFrameTimeMetric(presentTimeSummary.minFrameTime));
        runData.addMetric("run_" + runIndex + ".present_max_frametime", getFrameTimeMetric(presentTimeSummary.maxFrameTime));
        runData.addMetric("run_" + runIndex + ".present_frametime", getFrameTimeMetric(presentTimeSummary.avgFrameTime));

        runData.addMetric("run_" + runIndex + ".ready_min_fps", getFpsMetric(readyTimeSummary.minFPS));
        runData.addMetric("run_" + runIndex + ".ready_max_fps", getFpsMetric(readyTimeSummary.maxFPS));
        runData.addMetric("run_" + runIndex + ".ready_fps", getFpsMetric(readyTimeSummary.avgFPS));

        runData.addMetric("run_" + runIndex + ".ready_min_frametime", getFrameTimeMetric(readyTimeSummary.minFrameTime));
        runData.addMetric("run_" + runIndex + ".ready_max_frametime", getFrameTimeMetric(readyTimeSummary.maxFrameTime));
        runData.addMetric("run_" + runIndex + ".ready_frametime", getFrameTimeMetric(readyTimeSummary.avgFrameTime));



        printHistogram(frameTimes, runIndex);
    }

    private void onEnd(DeviceMetricData runData) {
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
                outputFile.write("VSync Period: " + mVSyncPeriod + "\n\n");

                if (mDeviceResultData.getEventsCount() == 0) {
                    CLog.w("No start intent given; assuming single run with no loading period to exclude.");
                }

                long startTime = 0L;
                int runIndex = 0;
                for (ResultDataProto.Event e : mDeviceResultData.getEventsList()) {
                    if (e.getType() != ResultDataProto.Event.Type.START_LOOP) {
                        continue;
                    }

                    long endTime = e.getTimestamp() * 1000000;  /* ms to ns */

                    if (startTime != 0) {
                        processTimestampsSlice(runIndex++, startTime, endTime, outputFile, runData);
                    }
                    startTime = endTime;
                }

                processTimestampsSlice(runIndex, startTime, mElapsedTimes.get(mElapsedTimes.size() - 1).getActualPresentTime(), outputFile, runData);

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

    void printHistogram(Collection<Long> frameTimes, int runIndex) {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Histogram histogram =
                    new Histogram(frameTimes, mVSyncPeriod / 2L, null, 5 * mVSyncPeriod);
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

    private Metric.Builder getFpsMetric(double value) {
        return Metric.newBuilder()
            .setUnit("fps")
            .setDirection(Directionality.UP_BETTER)
            .setType(DataType.PROCESSED)
            .setMeasurements(Measurements.newBuilder().setSingleDouble(value));
    }


    private Metric.Builder getFrameTimeMetric(long value) {
        return Metric.newBuilder()
            .setUnit("ns")
            .setDirection(Directionality.DOWN_BETTER)
            .setType(DataType.PROCESSED)
            .setMeasurements(Measurements.newBuilder().setSingleInt(value));
    }

    private class MetricSummary {
        public long totalTimeNs = 0;
        public long minFrameTime = Long.MAX_VALUE;
        public long maxFrameTime = 0;
        public long avgFrameTime = 0;
        public double minFPS = Double.MAX_VALUE;
        public double maxFPS = 0.0;
        public double avgFPS = 0.0;

        public String toString() {
            return ("max Frame Time: " + maxFrameTime + " ns\t\tmin FPS = " + minFPS + " fps\n" +
                    "min Frame Time: " + minFrameTime + " ns\t\tmax FPS = " + maxFPS + " fps\n" +
                    "avg Frame Time: " + avgFrameTime + " ns\t\tavg FPS = " + avgFPS + " fps");
        }

        private void processTimestamp(long timeDiff) {
            double currentFPS = 1.0e9 / timeDiff;
            minFPS = (currentFPS < minFPS) ? currentFPS : minFPS;
            maxFPS = (currentFPS > maxFPS) ? currentFPS : maxFPS;

            minFrameTime = (timeDiff < minFrameTime) ? timeDiff : minFrameTime;
            maxFrameTime = (timeDiff > maxFrameTime) ? timeDiff : maxFrameTime;

            totalTimeNs += timeDiff;
        }

        private void processAverages(int numOfTimestamps) {
            avgFPS = numOfTimestamps * 1.0e9 / totalTimeNs;
            avgFrameTime = totalTimeNs / numOfTimestamps;
        }
    }
}