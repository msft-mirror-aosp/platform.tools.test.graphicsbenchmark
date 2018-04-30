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

package com.android.graphics.benchmark.testtype;

import com.android.graphics.benchmark.metric.GraphicsBenchmarkMetricCollector;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.result.TestDescription;
import com.android.graphics.benchmark.ApkInfo;
import com.android.graphics.benchmark.ApkListXmlParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import com.google.common.io.ByteStreams;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;

public class GraphicsBenchmarkHostsideController implements IShardableTest, IDeviceTest {
    // Package and class of the device side test.
    private static final String PACKAGE = "com.android.graphics.benchmark.device";
    private static final String CLASS = PACKAGE + ".GraphicsBenchmarkTest";

    private static final String AJUR_RUNNER = "android.support.test.runner.AndroidJUnitRunner";
    private static final long DEFAULT_TEST_TIMEOUT_MS = 10 * 60 * 1000L; //10min
    private static final long DEFAULT_MAX_TIMEOUT_TO_OUTPUT_MS = 10 * 60 * 1000L; //10min

    private ITestDevice mDevice;
    private List<ApkInfo> mApks = null;
    private File mApkInfoFile;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Option(name = "apk-dir",
            description = "Directory contains the APKs for benchmarks",
            importance = Option.Importance.ALWAYS,
            mandatory = true)
    private String mApkDir;

    @Option(name = "apk-info",
            description = "A JSON file describing the list of APKs",
            importance = Option.Importance.ALWAYS)
    private String mApkInfoFileName;

    @Override
    public Collection<IRemoteTest> split(int shardCountHint) {
        initApkList();
        List<IRemoteTest> shards = new ArrayList<>();
        for(int i = 0; i < shardCountHint; i++) {
            if (i >= mApks.size()) {
                break;
            }
            List<ApkInfo> apkInfo = new ArrayList<>();
            for(int j = i; j < mApks.size(); j += shardCountHint) {
                apkInfo.add(mApks.get(j));
            }
            GraphicsBenchmarkHostsideController shard = new GraphicsBenchmarkHostsideController();
            shard.mApks = apkInfo;
            shard.mApkDir = mApkDir;

            shards.add(shard);
        }
        return shards;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Map<String, String> runMetrics = new HashMap<>();

        initApkList();
        getDevice().pushFile(mApkInfoFile, ApkInfo.APK_LIST_LOCATION);

        for (ApkInfo apk : mApks) {
            getDevice().installPackage(new File(mApkDir, apk.getFileName()), true);
            GraphicsBenchmarkMetricCollector.setAppLayerName(apk);

            // Might seem counter-intuitive, but the easiest way to get per-package results is
            // to put this call and the corresponding testRunEnd inside the for loop for now
            listener.testRunStarted("graphicsbenchmark", mApks.size());

            // TODO: Migrate to TF TestDescription when available
            TestDescription identifier = new TestDescription(CLASS, "run[" + apk.getName() + "]");

            Map<String, String> testMetrics = new HashMap<>();
            // TODO: Populate metrics

            listener.testStarted(identifier);
            runDeviceTests(PACKAGE, CLASS, "run[" + apk.getName() + "]");
            listener.testEnded(identifier, testMetrics);
            listener.testRunEnded(0, runMetrics);
        }
    }

    private void initApkList() {
        if (mApks != null) {
            return;
        }
        if (mApkInfoFileName != null) {
            mApkInfoFile = new File(mApkInfoFileName);
        } else {
            String resource = "/com/android/graphics/benchmark/apk-info.xml";
            try(InputStream inputStream = ApkInfo.class.getResourceAsStream(resource)) {
                if (inputStream == null) {
                    throw new FileNotFoundException("Unable to find resource: " + resource);
                }
                mApkInfoFile = File.createTempFile("apk-info", ".xml");
                try (OutputStream ostream = new FileOutputStream(mApkInfoFile)) {
                    ByteStreams.copy(inputStream, ostream);
                }
                mApkInfoFile.deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ApkListXmlParser parser = new ApkListXmlParser();
        try {
            mApks = parser.parse(mApkInfoFile);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }


    // TODO: Migrate to use BaseHostJUnit4Test when available.
    /**
     * Method to run an installed instrumentation package.
     *
     * @param pkgName the name of the package to run.
     * @param testClassName the name of the test class to run.
     * @param testMethodName the name of the method to run.
     */
    private void runDeviceTests(String pkgName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner =
                new RemoteAndroidTestRunner(pkgName, AJUR_RUNNER, getDevice().getIDevice());

        testRunner.setMethodName(testClassName, testMethodName);

        testRunner.addInstrumentationArg(
                "timeout_msec", Long.toString(DEFAULT_TEST_TIMEOUT_MS));
        testRunner.setMaxTimeout(DEFAULT_MAX_TIMEOUT_TO_OUTPUT_MS, TimeUnit.MILLISECONDS);

        CollectingTestListener listener = new CollectingTestListener();
        getDevice().runInstrumentationTests(testRunner, listener);
    }
}
