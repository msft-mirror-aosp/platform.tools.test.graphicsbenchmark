package com.android.game.qualification.metric;


import static com.android.game.qualification.metric.MetricSummary.TimeType.PRESENT;
import static com.android.game.qualification.metric.MetricSummary.TimeType.READY;

import static org.junit.Assert.assertEquals;

import com.android.game.qualification.metric.MetricSummary.LoopSummary;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

@RunWith(JUnit4.class)
public class MetricSummaryTest {

    private final double EPSILON = Math.ulp(1e9);

    @Test
    public void testBasicLoop() {
        LoopSummary summary = new LoopSummary();
        summary.addFrameTime(1);
        summary.processFrameTimes();
        assertEquals(1, summary.getAvgFrameTime(), EPSILON);
        assertEquals(1, summary.getMinFrameTime());
        assertEquals(1, summary.getMaxFrameTime());
        assertEquals(1e9, summary.getAvgFPS(), EPSILON);
        assertEquals(1e9, summary.getMinFPS(), EPSILON);
        assertEquals(1e9, summary.getMaxFPS(), EPSILON);
        assertEquals(1, summary.get90thPercentile());
        assertEquals(1, summary.get95thPercentile());
        assertEquals(1, summary.get99thPercentile());
    }

    @Test
    public void testSimpleLoop() {
        LoopSummary summary = new LoopSummary();
        summary.addFrameTime(1);
        summary.addFrameTime(2);
        summary.addFrameTime(3);
        summary.processFrameTimes();
        assertEquals(2, summary.getAvgFrameTime(), EPSILON);
        assertEquals(1, summary.getMinFrameTime());
        assertEquals(3, summary.getMaxFrameTime());
        assertEquals(1e9 / 2, summary.getAvgFPS(), EPSILON);
        assertEquals(1e9 / 3, summary.getMinFPS(), EPSILON);
        assertEquals(1e9 / 1, summary.getMaxFPS(), EPSILON);
        assertEquals(3, summary.get90thPercentile());
        assertEquals(3, summary.get95thPercentile());
        assertEquals(3, summary.get99thPercentile());
    }

    @Test
    public void testLargeDataSetLoop() {
        LoopSummary summary = new LoopSummary();
        for (int i = 0; i < 1000; i++) {
            summary.addFrameTime(i + 1);
        }
        summary.processFrameTimes();
        assertEquals(500.5, summary.getAvgFrameTime(), EPSILON);
        assertEquals(1, summary.getMinFrameTime());
        assertEquals(1000, summary.getMaxFrameTime());
        assertEquals(1e9 / 500.5, summary.getAvgFPS(), EPSILON);
        assertEquals(1e9 / 1000, summary.getMinFPS(), EPSILON);
        assertEquals(1e9 / 1, summary.getMaxFPS(), EPSILON);
        assertEquals(900, summary.get90thPercentile());
        assertEquals(950, summary.get95thPercentile());
        assertEquals(990, summary.get99thPercentile());
    }

    @Test
    public void testConversion() {
        MetricSummary summary = new MetricSummary();
        summary.beginLoop();
        summary.addFrameTime(PRESENT, 1);
        summary.addFrameTime(PRESENT, 2);
        summary.addFrameTime(PRESENT, 3);
        summary.addFrameTime(READY, 10);
        summary.addFrameTime(READY, 10);
        summary.addFrameTime(READY, 10);
        summary.endLoop();
        summary.beginLoop();
        summary.addFrameTime(PRESENT, 4);
        summary.addFrameTime(PRESENT, 5);
        summary.addFrameTime(READY, 20);
        summary.addFrameTime(READY, 20);
        summary.endLoop();

        assertEquals(3, summary.getAvgFrameTime(), EPSILON);

        DeviceMetricData runData = new DeviceMetricData(new InvocationContext());
        summary.addToMetricData(runData);
        HashMap<String, MetricMeasurement.Metric> metrics = new HashMap<>();
        runData.addToMetrics(metrics);

        MetricSummary result = MetricSummary.parseRunMetrics(new InvocationContext(), metrics);
        assertEquals(summary, result);
        assertEquals(summary.getAvgFrameTime(), result.getAvgFrameTime(), EPSILON);
    }
}