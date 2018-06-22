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

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.game.qualification.metric.MetricSummary;
import com.android.tradefed.config.Option;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Result reporter for game core certification.
 */
public class GameQualificationResultReporter extends CollectingTestListener implements ILogSaverListener {
    private static final String TAG = GameQualificationResultReporter.class.getSimpleName();

    @Option(name = "suppress-passed-tests", description = "For functional tests, ommit summary for "
            + "passing tests, only print failed and ignored ones")
    private boolean mSuppressPassedTest = false;

    private Map<TestDescription, MetricSummary> summaries = new ConcurrentHashMap<>();
    private List<LogFile> mLogFiles = new ArrayList<>();

    /**
     * Collect metrics produces by
     * {@link com.android.game.qualification.metric.GameQualificationMetricCollector}.
     */
    @Override
    public void testEnded(TestDescription testId, long elapsedTime, HashMap<String, Metric> metrics) {
        super.testEnded(testId, elapsedTime, metrics);
        if (!metrics.isEmpty()) {
            MetricSummary summary = MetricSummary.parseRunMetrics(getInvocationContext(), metrics);
            summaries.put(testId, summary);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource dataStream,
            LogFile logFile) {
        super.testLogSaved(dataName, dataType, dataStream, logFile);
        mLogFiles.add(logFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        // Ignore
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        Log.logAndDisplay(LogLevel.INFO, TAG, getInvocationSummary());
    }

    /**
     * Get the invocation summary as a string.
     */
    private String getInvocationSummary() {
        if (getRunResults().isEmpty() && mLogFiles.isEmpty()) {
            return "No test results\n";
        }
        StringBuilder sb = new StringBuilder();

        // Print location of log files.
        if (!mLogFiles.isEmpty()) {
            sb.append("Log Files:\n");
            for (LogFile logFile : mLogFiles) {
                final String url = logFile.getUrl();
                sb.append(String.format("  %s\n", url != null ? url : logFile.getPath()));
            }
            sb.append("\n");
        }

        // Print out device test results.
        sb.append("Test Results:\n");
        for (TestRunResult testRunResult : getRunResults()) {
            sb.append(getTestRunSummary(testRunResult));
        }

        // Print workload run metrics.
        for (Map.Entry<TestDescription, MetricSummary> entry : summaries.entrySet()) {
            sb.append(String.format("\n%s Metrics:\n%s\n", entry.getKey(), entry.getValue()));
        }

        // Determine certification level.
        if (hasFailedTests()) {
            sb.append("Certification failed because the following tests failed:\n");
            for (TestRunResult testRunResult : getRunResults()) {
                for (TestDescription test : testRunResult.getFailedTests()) {
                    sb.append('\t');
                    sb.append(test.toString());
                    sb.append('\n');
                }
            }
        } else {
            // TODO: Actually check if run metrics are above threshold.
            sb.append("Certification completed.\n");
        }
        return "Test results:\n" + sb.toString().trim() + "\n";
    }

    /**
     * Get the test run summary as a string including run metrics.
     */
    private String getTestRunSummary(TestRunResult testRunResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s:", testRunResult.getName()));
        if (testRunResult.getNumTests() > 0) {
            sb.append(String.format(" %d Test%s, %d Passed, %d Failed, %d Ignored",
                    testRunResult.getNumCompleteTests(),
                    testRunResult.getNumCompleteTests() == 1 ? "" : "s", // Pluralize Test
                    testRunResult.getNumTestsInState(TestStatus.PASSED),
                    testRunResult.getNumAllFailedTests(),
                    testRunResult.getNumTestsInState(TestStatus.IGNORED)));
        } else if (testRunResult.getRunMetrics().size() == 0) {
            sb.append(" No results");
        }
        sb.append("\n");
        Map<TestDescription, TestResult> testResults = testRunResult.getTestResults();
        for (Map.Entry<TestDescription, TestResult> entry : testResults.entrySet()) {
            if (mSuppressPassedTest && TestStatus.PASSED.equals(entry.getValue().getStatus())) {
                continue;
            }
            sb.append(getTestSummary(entry.getKey(), entry.getValue()));
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Get the test summary as string including test metrics.
     */
    private String getTestSummary(TestDescription testId, TestResult testResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %s: %s (%dms)\n", testId.toString(), testResult.getStatus(),
                testResult.getEndTime() - testResult.getStartTime()));
        String stack = testResult.getStackTrace();
        if (stack != null && !stack.isEmpty()) {
            sb.append("  stack=\n");
            String lines[] = stack.split("\\r?\\n");
            for (String line : lines) {
                sb.append(String.format("    %s\n", line));
            }
        }
        return sb.toString();
    }
}
