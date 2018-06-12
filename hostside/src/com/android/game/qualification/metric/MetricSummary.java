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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Directionality;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    };
    private int loopCount = 0;
    private Map<TimeType, List<LoopSummary>> summaries = new HashMap<>();

    public static MetricSummary parseRunMetrics(HashMap<String, Metric> metrics) {
        MetricSummary result = new MetricSummary();
        result.loopCount = (int)metrics.get("loop_count").getMeasurements().getSingleInt();
        for (int i = 0; i < result.loopCount; i++) {
            for (TimeType type : TimeType.values()) {
                result.summaries.get(type).add(new LoopSummary());
                result.getLastestSummary(type).parseRunMetrics(type, i, metrics);
            }
        }
        return result;
    }


    public MetricSummary() {
        for (TimeType type : TimeType.values()) {
            summaries.put(type, new ArrayList<>());
        }
    }

    private LoopSummary getLastestSummary(TimeType type) {
        Preconditions.checkState(loopCount > 0, "First loop has not been started.");
        List<LoopSummary> list = summaries.get(type);
        return list.get(list.size() - 1);
    }

    public void addFrameTime(TimeType type, long frameTime) {
        LoopSummary summary = getLastestSummary(type);
        summary.addFrameTime(frameTime);
    }
    public void beginLoop() {
        loopCount++;
        for (TimeType type : TimeType.values()) {
            summaries.get(type).add(new LoopSummary());
        }
    }

    public void endLoop() {
        for (TimeType type : TimeType.values()) {
            getLastestSummary(type).processFrameTimes();
        }
    }

    public double getAvgFrameTime() {
        double totalTime = 0;
        int numFrames = 0;
        for (LoopSummary summary : summaries.get(TimeType.PRESENT)) {
            totalTime += summary.getAvgFrameTime() * summary.getCount();
            numFrames += summary.getCount();
        }
        return totalTime / numFrames;
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

    private Metric.Builder getFrameTimeMetric(double value) {
        return Metric.newBuilder()
                .setUnit("ns")
                .setDirection(Directionality.DOWN_BETTER)
                .setType(DataType.PROCESSED)
                .setMeasurements(Measurements.newBuilder().setSingleDouble(value));
    }

    private static String getMetricKey(TimeType type, int loopIndex, String label) {
        return "run_" + loopIndex + "." + type.name().toLowerCase(Locale.US) + "_" + label;
    }

    public void addToMetricData(DeviceMetricData runData) {
        runData.addMetric(
                "loop_count",
                Metric.newBuilder()
                        .setType(DataType.PROCESSED)
                        .setMeasurements(Measurements.newBuilder().setSingleInt(loopCount)));
        for (int i = 0; i < loopCount; i++) {
            for (TimeType type : TimeType.values()) {
                LoopSummary summary = summaries.get(type).get(i);
                runData.addMetric(
                        getMetricKey(type, i, "frame_count"),
                        Metric.newBuilder()
                                .setType(DataType.PROCESSED)
                                .setMeasurements(
                                        Measurements.newBuilder()
                                                .setSingleInt(summary.getCount())));
                runData.addMetric(
                        getMetricKey(type, i, "min_fps"),
                        getFpsMetric(summary.getMinFPS()));
                runData.addMetric(
                        getMetricKey(type, i, "max_fps"),
                        getFpsMetric(summary.getMaxFPS()));
                runData.addMetric(
                        getMetricKey(type, i, "fps"),
                        getFpsMetric(summary.getAvgFPS()));

                runData.addMetric(
                        getMetricKey(type, i, "min_frametime"),
                        getFrameTimeMetric(summary.getMinFrameTime()));
                runData.addMetric(
                        getMetricKey(type, i, "max_frametime"),
                        getFrameTimeMetric(summary.getMaxFrameTime()));
                runData.addMetric(
                        getMetricKey(type, i, "frametime"),
                        getFrameTimeMetric(summary.getAvgFrameTime()));
                runData.addMetric(
                        getMetricKey(type, i, "90th_percentile"),
                        getFrameTimeMetric(summary.get90thPercentile()));
                runData.addMetric(
                        getMetricKey(type, i, "95th_percentile"),
                        getFrameTimeMetric(summary.get95thPercentile()));
                runData.addMetric(
                        getMetricKey(type, i, "99th_percentile"),
                        getFrameTimeMetric(summary.get99thPercentile()));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricSummary summary = (MetricSummary) o;
        return loopCount == summary.loopCount &&
                Objects.equals(summaries, summary.summaries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loopCount, summaries);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        // Report primary metrics.
        sb.append("Summary\n");
        sb.append("-------\n");
        sb.append(String.format("Average Frame Time: %.3f ms\n\n", nsToMs(getAvgFrameTime())));

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

    private static double nsToMs(long value) {
        return value / 1e6;
    }

    private static double nsToMs(double value) {
        return value / 1e6;
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

        public void parseRunMetrics(TimeType type, int runIndex, HashMap<String, Metric> runMetrics) {
            count = getMetricLongValue(type, runIndex, "frame_count", runMetrics);
            minFrameTime = getMetricLongValue(type, runIndex, "min_frametime", runMetrics);
            maxFrameTime = getMetricLongValue(type, runIndex, "max_frametime", runMetrics);
            avgFrameTime = getMetricDoubleValue(type, runIndex, "frametime", runMetrics);
            percentile90 = getMetricLongValue(type, runIndex, "90th_percentile", runMetrics);
            percentile95 = getMetricLongValue(type, runIndex, "95th_percentile", runMetrics);
            percentile99 = getMetricLongValue(type, runIndex, "99th_percentile", runMetrics);
            processed = true;
        }

        private double getMetricDoubleValue(
                TimeType type, int runIndex, String metric, HashMap<String, Metric> runMetrics) {
            Metric m = runMetrics.get(getMetricKey(type, runIndex, metric));
            if (!m.hasMeasurements()) {
                throw new RuntimeException();
            }
            return m.getMeasurements().getSingleDouble();
        }

        private long getMetricLongValue(
                TimeType type, int runIndex, String metric, HashMap<String, Metric> runMetrics) {
            Metric m = runMetrics.get(getMetricKey(type, runIndex, metric));
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
                    minFrameTime,
                    maxFrameTime,
                    avgFrameTime,
                    percentile90,
                    percentile95,
                    percentile99);
        }

        public String toString() {
            return String.format(
                    "avg Frame Time: %7.3f ms\t\tavg FPS = %.3f fps\n"
                            + "max Frame Time: %7.3f ms\t\tmin FPS = %.3f fps\n"
                            + "min Frame Time: %7.3f ms\t\tmax FPS = %.3f fps\n"
                            + "90th Percentile Frame Time: %7.3f ms\n"
                            + "95th Percentile Frame Time: %7.3f ms\n"
                            + "99th Percentile Frame Time: %7.3f ms\n",
                    nsToMs(getAvgFrameTime()), getAvgFPS(),
                    nsToMs(getMaxFrameTime()), getMinFPS(),
                    nsToMs(getMinFrameTime()), getMaxFPS(),
                    nsToMs(get90thPercentile()),
                    nsToMs(get95thPercentile()),
                    nsToMs(get99thPercentile()));
        }
    }


}
