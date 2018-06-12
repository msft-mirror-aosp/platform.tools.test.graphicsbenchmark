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

import com.google.common.base.Preconditions;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Summary of frame time metrics data.
 */
class MetricSummary {
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

    public void processFrameTimes() {
        Preconditions.checkState(!processed, "Frames times were already processed");
        avgFrameTime = (double)totalTimeNs / count;
        calcPercentiles();
        processed = true;
    }

    private void calcPercentiles() {
        long onePercent = (long)(frameTimes.size() * 0.01);
        long fivePercent = (long)(frameTimes.size() * 0.05);
        long tenPercent = (long)(frameTimes.size() * 0.10);
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

    public String toString() {
        return String.format(
                "max Frame Time: %d ns\t\tmin FPS = %f fps\n"
                        + "min Frame Time: %d ns\t\tmax FPS = %f fps\n"
                        + "avg Frame Time: %f ns\t\tavg FPS = %f fps\n"
                        + "90th Percentage Frame Time: %d ns\n"
                        + "95th Percentage Frame Time: %d ns\n"
                        + "99th Percentage Frame Time: %d ns\n",
                getMaxFrameTime(),  getMinFPS(),
                getMinFrameTime(),  getMaxFPS(),
                getAvgFrameTime(),  getAvgFPS(),
                get90thPercentile(),
                get95thPercentile(),
                get99thPercentile());
    }

}
