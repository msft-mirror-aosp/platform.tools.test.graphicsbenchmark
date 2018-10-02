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
import com.android.annotations.VisibleForTesting;
import com.android.game.qualification.CertificationRequirements;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Directionality;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Summary of frame time metrics data.
 */
public class MetricSummary {
    public enum TimeType {
        PRESENT,
        READY
    }

    private int loopCount;
    private double jankRate;
    private long loadTimeMs;
    private Map<TimeType, List<LoopSummary>> summaries;

    private MetricSummary(
            int loopCount,
            double jankRate,
            long loadTimeMs, Map<TimeType, List<LoopSummary>> summaries) {
        this.jankRate = jankRate;
        this.loopCount = loopCount;
        this.loadTimeMs = loadTimeMs;
        this.summaries = summaries;
    }

    @Nullable
    public static MetricSummary parseRunMetrics(
            IInvocationContext context, HashMap<String, Metric> metrics) {
        int loopCount = 0;
        if (metrics.containsKey("loop_count")) {
            loopCount = (int) metrics.get("loop_count").getMeasurements().getSingleInt();
        }

        if (loopCount == 0) {
            return null;
        }

        Map<TimeType, List<LoopSummary>> summaries = new LinkedHashMap<>();
        for (TimeType type : TimeType.values()) {
            summaries.put(type, new ArrayList<>());
            for (int i = 0; i < loopCount; i++) {
                LoopSummary loopSummary = new LoopSummary();
                loopSummary.parseRunMetrics(context, type, i, metrics);
                summaries.get(type).add(loopSummary);
            }
        }
        return new MetricSummary(
                loopCount,
                metrics.get("jank_rate").getMeasurements().getSingleDouble(),
                metrics.get("load_time").getMeasurements().getSingleInt(),
                summaries);
    }

    public double getJankRate() {
        return jankRate;
    }

    public long getLoadTimeMs() {
        return loadTimeMs;
    }

    public void addToMetricData(DeviceMetricData runData) {
        runData.addMetric(
                "loop_count",
                Metric.newBuilder()
                        .setType(DataType.PROCESSED)
                        .setMeasurements(Measurements.newBuilder().setSingleInt(loopCount)));
        runData.addMetric(
                "jank_rate",
                Metric.newBuilder()
                        .setType(DataType.PROCESSED)
                        .setMeasurements(Measurements.newBuilder().setSingleDouble(getJankRate())));
        runData.addMetric(
                "load_time",
                Metric.newBuilder()
                        .setType(DataType.RAW)
                        .setMeasurements(Measurements.newBuilder().setSingleInt(loadTimeMs)));

        for (int i = 0; i < loopCount; i++) {
            for (TimeType type : TimeType.values()) {
                LoopSummary summary = summaries.get(type).get(i);
                summary.addToMetricData(runData, i, type);
            }
        }
    }

    private static Metric.Builder getNsMetric(long value) {
        return Metric.newBuilder()
                .setUnit("ns")
                .setDirection(Directionality.DOWN_BETTER)
                .setType(DataType.PROCESSED)
                .setMeasurements(Measurements.newBuilder().setSingleInt(value));
    }

    private static Metric.Builder getNsMetric(double value) {
        return Metric.newBuilder()
                .setUnit("ns")
                .setDirection(Directionality.DOWN_BETTER)
                .setType(DataType.PROCESSED)
                .setMeasurements(Measurements.newBuilder().setSingleDouble(value));
    }


    private static String getActualMetricKey(
            IInvocationContext context, TimeType type, int loopIndex, String label) {
        // DeviceMetricData automatically add the deviceName to the metric key if there are more
        // than one devices.  We don't really want or care about the device in the metric data, but
        // we need to get the actual key that was added in order to parse it correctly.
        if (context.getDevices().size() > 1) {
            String deviceName = context.getDeviceName(context.getDevices().get(0));
            return String.format("{%s}:%s", deviceName, getMetricKey(type, loopIndex, label));
        }
        return getMetricKey(type, loopIndex, label);
    }

    private static String getMetricKey(TimeType type, int loopIndex, String label) {
        return "run_" + loopIndex + "." + type.name().toLowerCase(Locale.US) + "_" + label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricSummary summary = (MetricSummary) o;
        return loopCount == summary.loopCount &&
                jankRate == summary.jankRate &&
                loadTimeMs == summary.loadTimeMs &&
                Objects.equals(summaries, summary.summaries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loopCount, jankRate, loadTimeMs, summaries);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        // Report primary metrics.
        sb.append("Summary\n");
        sb.append("-------\n");
        sb.append(String.format("'Jank' rate: %.3f\n", getJankRate()));
        sb.append("Load time: ");
        if (getLoadTimeMs() == -1) {
            sb.append("unknown");
        } else {
            sb.append(getLoadTimeMs());
            sb.append(" ms\n");
        }
        sb.append("\n");

        // Report secondary metrics.
        sb.append("Details\n");
        sb.append("-------\n");


        for (int i = 0; i < loopCount; i++) {
            if (summaries.get(TimeType.PRESENT).get(i).getCount() == 0) {
                continue;
            }
            sb.append("Loop ");
            sb.append(i);
            sb.append('\n');
            for (TimeType type : TimeType.values()) {
                sb.append(type);
                sb.append(" Time Statistics\n");
                sb.append(summaries.get(type).get(i));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static float msToNs(float value) {
        return value * 1e6f;
    }

    private static double nsToMs(long value) {
        return value / 1e6;
    }

    private static double nsToMs(double value) {
        return value / 1e6;
    }


    public static class Builder {
        @Nullable
        private CertificationRequirements mRequirements;
        private double jankScore = 0;
        private long mVSyncPeriodNs;
        private int loopCount = 0;
        private long loadTimeMs = -1;
        private long totalTimeNs = 0;
        private Map<TimeType, List<LoopSummary>> summaries = new LinkedHashMap<>();

        public Builder(@Nullable CertificationRequirements requirements, long vSyncPeriodNs) {
            mRequirements = requirements;
            mVSyncPeriodNs = vSyncPeriodNs;
            for (TimeType type : TimeType.values()) {
                summaries.put(type, new ArrayList<>());
            }
        }

        private LoopSummary getLatestSummary(TimeType type) {
            Preconditions.checkState(loopCount > 0, "First loop has not been started.");
            List<LoopSummary> list = summaries.get(type);
            return list.get(list.size() - 1);
        }

        public void setLoadTimeMs(long loadTimeMs) {
            this.loadTimeMs = loadTimeMs;
        }

        public void addFrameTime(TimeType type, long frameTimeNs) {
            if (type == TimeType.PRESENT) {
                totalTimeNs += frameTimeNs;
                if (mRequirements != null) {
                    float targetFrameTime = msToNs(mRequirements.getFrameTime());
                    long roundedFrameTimeNs =
                            Math.round(frameTimeNs / (double)mVSyncPeriodNs) * mVSyncPeriodNs;
                    if (roundedFrameTimeNs > targetFrameTime) {
                        double score = (roundedFrameTimeNs - targetFrameTime) / targetFrameTime;
                        jankScore += score;
                    }
                }
            }
            LoopSummary summary = getLatestSummary(type);
            summary.addFrameTime(frameTimeNs);
        }

        public void beginLoop() {
            loopCount++;
            for (TimeType type : TimeType.values()) {
                summaries.get(type).add(new LoopSummary());
            }
        }

        public void endLoop() {
            for (TimeType type : TimeType.values()) {
                getLatestSummary(type).processFrameTimes();
            }
        }

        public MetricSummary build() {
            return new MetricSummary(
                    loopCount,
                    jankScore * 1000000000 / totalTimeNs,  /* jank rate per second */
                    loadTimeMs,
                    summaries);
        }
    }

    @VisibleForTesting
    static class LoopSummary {
        private boolean processed = false;
        private long count = 0;
        private long totalTimeNs = 0;
        private long minFrameTime = Long.MAX_VALUE;
        private long maxFrameTime = 0;
        private double avgFrameTime = 0;
        private long percentile90 = -1;
        private long percentile95 = -1;
        private long percentile99 = -1;
        private PriorityQueue<Long> frameTimes = new PriorityQueue<>(Comparator.reverseOrder());

        public long getCount() {
            return count;
        }

        public long getDuration() {
            return totalTimeNs;
        }

        public long getMinFrameTime() {
            return minFrameTime;
        }

        public long getMaxFrameTime() {
            return maxFrameTime;
        }

        public double getAvgFrameTime() {
            Preconditions.checkState(processed, "processFrameTimes must be called.");
            return avgFrameTime;
        }

        public double getMinFPS() {
            return 1.0e9 / maxFrameTime;
        }

        public double getMaxFPS() {
            return 1.0e9 / minFrameTime;
        }

        public double getAvgFPS() {
            Preconditions.checkState(processed, "processFrameTimes must be called.");
            return 1.0e9 / avgFrameTime;
        }

        public long get90thPercentile() {
            Preconditions.checkState(processed, "processFrameTimes must be called.");
            return percentile90;
        }

        public long get95thPercentile() {
            Preconditions.checkState(processed, "processFrameTimes must be called.");
            return percentile95;
        }

        public long get99thPercentile() {
            Preconditions.checkState(processed, "processFrameTimes must be called.");
            return percentile99;
        }

        public void addFrameTime(long timeDiff) {
            minFrameTime = (timeDiff < getMinFrameTime()) ? timeDiff : getMinFrameTime();
            maxFrameTime = (timeDiff > getMaxFrameTime()) ? timeDiff : getMaxFrameTime();
            totalTimeNs = totalTimeNs + timeDiff;
            frameTimes.add(timeDiff);
            count++;
        }

        @VisibleForTesting
        void processFrameTimes() {
            Preconditions.checkState(!processed, "Frames times were already processed");
            processed = true;
            if (count == 0) {
                return;
            }
            avgFrameTime = (double) totalTimeNs / count;
            calcPercentiles();
        }

        private void calcPercentiles() {
            long onePercent = (long) (frameTimes.size() * 0.01);
            long fivePercent = (long) (frameTimes.size() * 0.05);
            long tenPercent = (long) (frameTimes.size() * 0.10);
            for (int i = 0; i <= tenPercent; i++) {
                if (i == onePercent) {
                    percentile99 = frameTimes.peek();
                }
                if (i == fivePercent) {
                    percentile95 = frameTimes.peek();
                }
                if (i == tenPercent) {
                    percentile90 = frameTimes.peek();
                }
                frameTimes.poll();
            }
        }

        private void addToMetricData(DeviceMetricData runData, int index, TimeType type) {
            runData.addMetric(
                    getMetricKey(type, index, "frame_count"),
                    Metric.newBuilder()
                            .setType(DataType.PROCESSED)
                            .setMeasurements(
                                    Measurements.newBuilder()
                                            .setSingleInt(getCount())));
            runData.addMetric(
                    getMetricKey(type, index, "duration"),
                    getNsMetric(getDuration()));
            runData.addMetric(
                    getMetricKey(type, index, "min_frametime"),
                    getNsMetric(getMinFrameTime()));
            runData.addMetric(
                    getMetricKey(type, index, "max_frametime"),
                    getNsMetric(getMaxFrameTime()));
            runData.addMetric(
                    getMetricKey(type, index, "frametime"),
                    getNsMetric(getAvgFrameTime()));
            runData.addMetric(
                    getMetricKey(type, index, "90th_percentile"),
                    getNsMetric(get90thPercentile()));
            runData.addMetric(
                    getMetricKey(type, index, "95th_percentile"),
                    getNsMetric(get95thPercentile()));
            runData.addMetric(
                    getMetricKey(type, index, "99th_percentile"),
                    getNsMetric(get99thPercentile()));
        }

        public void parseRunMetrics(
                IInvocationContext context,
                TimeType type,
                int runIndex,
                HashMap<String, Metric> runMetrics) {
            count = getMetricLongValue(context, type, runIndex, "frame_count", runMetrics);
            totalTimeNs = getMetricLongValue(context, type, runIndex, "duration", runMetrics);
            minFrameTime = getMetricLongValue(context, type, runIndex, "min_frametime", runMetrics);
            maxFrameTime = getMetricLongValue(context, type, runIndex, "max_frametime", runMetrics);
            avgFrameTime = getMetricDoubleValue(context, type, runIndex, "frametime", runMetrics);
            percentile90 = getMetricLongValue(context, type, runIndex, "90th_percentile", runMetrics);
            percentile95 = getMetricLongValue(context, type, runIndex, "95th_percentile", runMetrics);
            percentile99 = getMetricLongValue(context, type, runIndex, "99th_percentile", runMetrics);
            processed = true;
        }

        private double getMetricDoubleValue(
                IInvocationContext context,
                TimeType type,
                int runIndex,
                String metric,
                HashMap<String, Metric> runMetrics) {
            Metric m = runMetrics.get(getActualMetricKey(context, type, runIndex, metric));
            if (!m.hasMeasurements()) {
                throw new RuntimeException();
            }
            return m.getMeasurements().getSingleDouble();
        }

        private long getMetricLongValue(
                IInvocationContext context,
                TimeType type,
                int runIndex,
                String metric,
                HashMap<String, Metric> runMetrics) {
            Metric m = runMetrics.get(getActualMetricKey(context, type, runIndex, metric));
            if (!m.hasMeasurements()) {
                throw new RuntimeException();
            }
            return m.getMeasurements().getSingleInt();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LoopSummary that = (LoopSummary) o;
            return processed == that.processed &&
                    count == that.count &&
                    totalTimeNs == that.totalTimeNs &&
                    minFrameTime == that.minFrameTime &&
                    maxFrameTime == that.maxFrameTime &&
                    Double.compare(that.avgFrameTime, avgFrameTime) == 0 &&
                    percentile90 == that.percentile90 &&
                    percentile95 == that.percentile95 &&
                    percentile99 == that.percentile99;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    processed,
                    count,
                    totalTimeNs,
                    minFrameTime,
                    maxFrameTime,
                    avgFrameTime,
                    percentile90,
                    percentile95,
                    percentile99);
        }

        public String toString() {
            return String.format(
                    "duration: %.3f ms\n"
                            + "avg Frame Time: %7.3f ms\t\tavg FPS = %.3f fps\n"
                            + "max Frame Time: %7.3f ms\t\tmin FPS = %.3f fps\n"
                            + "min Frame Time: %7.3f ms\t\tmax FPS = %.3f fps\n"
                            + "90th Percentile Frame Time: %7.3f ms\n"
                            + "95th Percentile Frame Time: %7.3f ms\n"
                            + "99th Percentile Frame Time: %7.3f ms\n",
                    nsToMs(getDuration()),
                    nsToMs(getAvgFrameTime()), getAvgFPS(),
                    nsToMs(getMaxFrameTime()), getMinFPS(),
                    nsToMs(getMinFrameTime()), getMaxFPS(),
                    nsToMs(get90thPercentile()),
                    nsToMs(get95thPercentile()),
                    nsToMs(get99thPercentile()));
        }
    }
}
