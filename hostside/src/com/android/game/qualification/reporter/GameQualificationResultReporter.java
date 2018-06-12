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
package com.android.game.qualification.reporter;

import com.android.game.qualification.metric.MetricSummary;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;

import java.util.HashMap;
import java.util.Map;

public class GameQualificationResultReporter extends CollectingTestListener implements ILogSaverListener {
    private Map<TestDescription, MetricSummary> summaries = new HashMap<>();

    @Override
    public void testFailed(TestDescription testId, String trace) {
        CLog.e("\nTest %s: failed \n stack: %s ", testId, trace);
    }

    @Override
    public void testAssumptionFailure(TestDescription testId, String trace) {
        CLog.e("\nTest %s: assumption failed \n stack: %s ", testId, trace);
    }

    /**
     * Collect metrics produces by
     * {@link com.android.game.qualification.metric.GameQualificationMetricCollector}.
     */
    @Override
    public void testEnded(TestDescription testId, long elapsedTime, HashMap<String, Metric> metrics) {
        super.testEnded(testId, elapsedTime, metrics);
        if (!metrics.isEmpty()) {
            MetricSummary summary = MetricSummary.parseRunMetrics(metrics);
            summaries.put(testId, summary);
        }
    }

    /**
     * Print out summary at the end.
     */
    @Override
    public void testRunEnded(long elapsedTime,
            HashMap<String, Metric> runMetrics) {
        // FIXME: figure out how to print this out at the end of everything.
        super.testRunEnded(elapsedTime, runMetrics);
        for (Map.Entry<TestDescription, MetricSummary> entry : summaries.entrySet()) {
            CLog.i(String.format("\n%s Metrics:\n%s\n", entry.getKey(), entry.getValue()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource dataStream,
            LogFile logFile) {
        CLog.i("Saved %s log to %s", dataName, logFile.getPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        // Ignore
    }

}
