package com.android.game.qualification.metric;


import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetricSummaryTest {

    private final double EPSILON = Math.ulp(1e9);

    @Test
    public void testBasic() {
        MetricSummary summary = new MetricSummary();
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
    public void testSimple() {
        MetricSummary summary = new MetricSummary();
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
    public void testLargeDataSet() {
        MetricSummary summary = new MetricSummary();
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
}