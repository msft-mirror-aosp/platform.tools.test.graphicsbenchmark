package com.android.game.qualification.metric;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Directionality;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.base.Preconditions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.PriorityQueue;

class LoopSummary {
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

    void addToMetricData(DeviceMetricData runData, int index, MetricSummary.TimeType type) {
        runData.addMetric(
                getMetricKey(type, index, "frame_count"),
                Metric.newBuilder()
                        .setType(MetricMeasurement.DataType.PROCESSED)
                        .setMeasurements(
                                MetricMeasurement.Measurements.newBuilder()
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
            MetricSummary.TimeType type,
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
            MetricSummary.TimeType type,
            int runIndex,
            String metric,
            HashMap<String, Metric> runMetrics) {
        Metric m = runMetrics.get(
                getActualMetricKey(context, type, runIndex, metric));
        if (!m.hasMeasurements()) {
            throw new RuntimeException();
        }
        return m.getMeasurements().getSingleDouble();
    }

    private long getMetricLongValue(
            IInvocationContext context,
            MetricSummary.TimeType type,
            int runIndex,
            String metric,
            HashMap<String, Metric> runMetrics) {
        Metric m = runMetrics.get(
                getActualMetricKey(context, type, runIndex, metric));
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

    private static Metric.Builder getNsMetric(long value) {
        return Metric.newBuilder()
                .setUnit("ns")
                .setDirection(MetricMeasurement.Directionality.DOWN_BETTER)
                .setType(MetricMeasurement.DataType.PROCESSED)
                .setMeasurements(MetricMeasurement.Measurements.newBuilder().setSingleInt(value));
    }

    private static Metric.Builder getNsMetric(double value) {
        return Metric.newBuilder()
                .setUnit("ns")
                .setDirection(Directionality.DOWN_BETTER)
                .setType(DataType.PROCESSED)
                .setMeasurements(Measurements.newBuilder().setSingleDouble(value));
    }

    private static String getActualMetricKey(
            IInvocationContext context, MetricSummary.TimeType type, int loopIndex, String label) {
        // DeviceMetricData automatically add the deviceName to the metric key if there are more
        // than one devices.  We don't really want or care about the device in the metric data, but
        // we need to get the actual key that was added in order to parse it correctly.
        if (context.getDevices().size() > 1) {
            String deviceName = context.getDeviceName(context.getDevices().get(0));
            return String.format("{%s}:%s", deviceName, getMetricKey(type, loopIndex, label));
        }
        return getMetricKey(type, loopIndex, label);
    }

    private static String getMetricKey(MetricSummary.TimeType type, int loopIndex, String label) {
        return "run_" + loopIndex + "." + type.name().toLowerCase(Locale.US) + "_" + label;
    }

    private static double nsToMs(long value) {
        return value / 1e6;
    }

    private static double nsToMs(double value) {
        return value / 1e6;
    }



}
