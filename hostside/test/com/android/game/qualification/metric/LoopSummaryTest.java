package com.android.game.qualification.metric;

import static org.junit.Assert.*;

import org.junit.Test;

public class LoopSummaryTest {
    private static final double EPSILON = Math.ulp(1e9);

    @Test
    public void testBasicLoop() {
        LoopSummary summary = new LoopSummary();
        summary.addFrameTime(1);
        summary.processFrameTimes();
        assertEquals(1, summary.getDuration());
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
        assertEquals(6, summary.getDuration());
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
        assertEquals(500500, summary.getDuration());
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