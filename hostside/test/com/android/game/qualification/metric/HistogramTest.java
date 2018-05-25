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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Test for {@link Histogram}.
 */
public class HistogramTest {

    @Test
    public void testSimple() throws IOException {
        List<Long> data = Arrays.asList(0L, 1L, 1L, 2L, 0L);
        Histogram histogram = new Histogram(data, 1L, null, null);

        Map<Long, Integer> counts = histogram.getCounts();
        assertEquals(2, (int)counts.get(0L));
        assertEquals(2, (int)counts.get(1L));
        assertEquals(1, (int)counts.get(2L));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        histogram.plotAscii(out, 2);
        assertEquals(" 0| ==\n 1| ==\n 2| =\n", out.toString());
    }

    @Test
    public void testCutoff() {
        List<Long> data = Arrays.asList(-2L, -1L, 0L, 1L, 2L);
        Histogram histogram = new Histogram(data, 1L,-1L, 1L);

        Map<Long, Integer> counts = histogram.getCounts();
        assertEquals(2, (int)counts.get(-1L));
        assertEquals(1, (int)counts.get(0L));
        assertEquals(2, (int)counts.get(1L));
    }

    @Test
    public void testPlotCutoff() throws IOException {
        List<Long> data = Arrays.asList(0L, 2L, 4L);
        Histogram histogram = new Histogram(data, 1L, 1L, 3L);

        Map<Long, Integer> counts = histogram.getCounts();
        assertEquals(1, (int)counts.get(1L));
        assertEquals(1, (int)counts.get(2L));
        assertEquals(1, (int)counts.get(3L));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        histogram.plotAscii(out, 1);
        assertEquals("<1| =\n 2| =\n>3| =\n", out.toString());
    }

    @Test
    public void testBuckets() {
        List<Long> data = Arrays.asList(0L, 1L, 2L, 3L, 4L);
        Histogram histogram = new Histogram(data, 3L, null, null);

        Map<Long, Integer> counts = histogram.getCounts();
        assertEquals(2, (int)counts.get(0L));
        assertEquals(3, (int)counts.get(3L));
    }

    /**
     * With even bucket size, a data point can be on the exact bucket boundary.  In that case, the
     * data point will be categorized into the higher bucket.
     */
    @Test
    public void testEvenBucketSize() {
        List<Long> data = Arrays.asList(-2L, -1L, 0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        Histogram histogram = new Histogram(data, 2L,-1L, 5L);

        Map<Long, Integer> counts = histogram.getCounts();
        assertEquals(1, (int)counts.get(-1L));
        assertEquals(2, (int)counts.get(0L));
        assertEquals(2, (int)counts.get(2L));
        assertEquals(2, (int)counts.get(4L));
        assertEquals(2, (int)counts.get(5L));
    }

}