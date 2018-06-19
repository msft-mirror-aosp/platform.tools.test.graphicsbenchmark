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

package com.android.game.qualification.testtype;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.game.qualification.ApkInfo;
import com.android.game.qualification.ApkListXmlParser;
import com.android.game.qualification.ResultData;
import com.android.game.qualification.metric.GameQualificationMetricCollector;
import com.android.game.qualification.proto.ResultDataProto;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.device.metric.IMetricCollectorReceiver;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.xml.sax.SAXException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;

public class GameQualificationHostsideController implements
        IShardableTest, IDeviceTest, IMetricCollectorReceiver, IInvocationContextReceiver {
    // Package and class of the device side test.
    private static final String PACKAGE = "com.android.game.qualification.device";
    private static final String CLASS = PACKAGE + ".GameQualificationTest";

    private static final String AJUR_RUNNER = "android.support.test.runner.AndroidJUnitRunner";
    private static final long DEFAULT_TEST_TIMEOUT_MS = 10 * 60 * 1000L; //10min
    private static final long DEFAULT_MAX_TIMEOUT_TO_OUTPUT_MS = 10 * 60 * 1000L; //10min

    private ITestDevice mDevice;
    private List<ApkInfo> mApks = null;
    private File mApkInfoFile;
    private Collection<IMetricCollector> mCollectors;
    private IInvocationContext mContext;
    @Nullable
    private GameQualificationMetricCollector mAGQMetricCollector = null;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Option(name = "apk-info",
            description = "An XML file describing the list of APKs for qualifications.",
            importance = Option.Importance.ALWAYS)
    private String mApkInfoFileName;

    @Option(name = "apk-dir",
            description =
                    "Directory contains the APKs for qualifications.  If --apk-info is not "
                            + "specified and a file named 'apk-info.xml' exists in --apk-dir, that "
                            + "file will be used as the apk-info.",
            importance = Option.Importance.ALWAYS)
    private String mApkDir;

    private String getApkDir() {
        if (mApkDir == null) {
            mApkDir = System.getenv("ANDROID_PRODUCT_OUT") + "/data/app";
        }
        return mApkDir;
    }

    @Override
    public void setMetricCollectors(List<IMetricCollector> list) {
        mCollectors = list;
        for (IMetricCollector collector : list) {
            if (collector instanceof GameQualificationMetricCollector) {
                mAGQMetricCollector = (GameQualificationMetricCollector) collector;
            }
        }
    }

    @Override
    public void setInvocationContext(IInvocationContext iInvocationContext) {
        mContext = iInvocationContext;
    }

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
            GameQualificationHostsideController shard = new GameQualificationHostsideController();
            shard.mApks = apkInfo;
            shard.mApkDir = getApkDir();
            shard.mApkInfoFileName = mApkInfoFileName;
            shard.mApkInfoFile = mApkInfoFile;

            shards.add(shard);
        }
        return shards;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        assert mAGQMetricCollector != null;
        for (IMetricCollector collector : mCollectors) {
            listener = collector.init(mContext, listener);
        }
        mAGQMetricCollector.setDevice(getDevice());

        HashMap<String, MetricMeasurement.Metric> runMetrics = new HashMap<>();

        initApkList();
        getDevice().pushFile(mApkInfoFile, ApkInfo.APK_LIST_LOCATION);
        String serial = getDevice().getSerialNumber();

        long startTime = System.currentTimeMillis();
        listener.testRunStarted("gamequalification", mApks.size());

        for (ApkInfo apk : mApks) {
            File apkFile = findApk(apk.getFileName());
            RunScriptResult setupResult = RunScriptResult.SUCCESS;
            if (apk.getScript() != null) {
                setupResult = runSetupScript(apk, serial);
            }
            if (apkFile != null && !setupResult.failed) {
                CLog.i("Installing %s on %s.", apkFile.getName(), getDevice().getSerialNumber());
                getDevice().installPackage(apkFile, true);
            }
            mAGQMetricCollector.setApkInfo(apk);

            HashMap<String, MetricMeasurement.Metric> testMetrics = new HashMap<>();

            // APK Test.
            TestDescription identifier = new TestDescription(CLASS, "run[" + apk.getName() + "]");
            listener.testStarted(identifier);

            boolean apkTestPassed = false;
            if (getDevice().getKeyguardState().isKeyguardShowing()) {
                listener.testFailed(
                        identifier,
                "Unable to unlock device: " + getDevice().getDeviceDescriptor());
            } else if (apkFile == null) {
                listener.testFailed(
                        identifier,
                        String.format(
                                "Missing APK.  Unable to find %s in %s.\n",
                                apk.getFileName(),
                                getApkDir()));
            } else if (setupResult.failed) {
                listener.testFailed(
                        identifier,
                        "Execution of setup script returned non-zero value:\n" + setupResult.output);
            } else {
                runDeviceTests(PACKAGE, CLASS, "run[" + apk.getName() + "]");
                ResultDataProto.Result resultData = retrieveResultData();
                mAGQMetricCollector.setDeviceResultData(resultData);

                if (mAGQMetricCollector.isAppTerminated()) {
                    listener.testFailed(identifier, "App was terminated");
                } else {
                    apkTestPassed = true;
                }
            }

            listener.testEnded(identifier, testMetrics);

            if (apkTestPassed) {
                // Screenshot test.
                TestDescription screenshotTestId =
                        new TestDescription(CLASS, "screenshotTest[" + apk.getName() + "]");
                listener.testStarted(screenshotTestId);
                try {
                    checkScreenshot(listener, screenshotTestId);
                } catch (DeviceNotAvailableException e) {
                    listener.testFailed(screenshotTestId, e.getMessage());
                }
                listener.testEnded(screenshotTestId, testMetrics);
            }
            getDevice().uninstallPackage(apk.getPackageName());
        }
        listener.testRunEnded(System.currentTimeMillis() - startTime, runMetrics);

    }

    private static class RunScriptResult {
        private static RunScriptResult SUCCESS = new RunScriptResult(false, "");

        private boolean failed;
        private String output;

        public RunScriptResult(boolean failed, String output) {
            this.failed = failed;
            this.output = output;
        }
    }

    /**
     * Execute setup script defined by the ApkInfo.
     *
     * @param apk ApkInfo.
     * @param deviceSerial Serial number of the device to be executed on.
     * @return Output string
     */
    private RunScriptResult runSetupScript(ApkInfo apk, String deviceSerial) {
        String cmd = apk.getScript();
        CLog.i(
                "Executing command: " + cmd + "\n"
                        + "Working directory: " + mApkInfoFile.getParent());
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.environment().put("ANDROID_SERIAL", deviceSerial);
            pb.directory(mApkInfoFile.getParentFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.MINUTES);
            if (!finished || p.exitValue() != 0) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ByteStreams.copy(p.getInputStream(), os);
                String output = os.toString(StandardCharsets.UTF_8.name());
                if (!finished) {
                    output += "\n***TIMEOUT waiting for script to complete.***";
                    p.destroy();
                }
                return new RunScriptResult(true, output);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return RunScriptResult.SUCCESS;
    }

    private ResultDataProto.Result retrieveResultData() throws DeviceNotAvailableException {
        File resultFile = getDevice().pullFileFromExternal(ResultData.RESULT_FILE_LOCATION);

        if (resultFile != null) {
            try (InputStream inputStream = new FileInputStream(resultFile)) {
                return ResultDataProto.Result.parseFrom(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /** Find an apk in the apk-dir directory */
    private File findApk(String filename) {
        File file = new File(getApkDir(), filename);
        if (file.exists()) {
            return file;
        }
        // If a default sample app is named Sample.apk, it is outputted to
        // $ANDROID_PRODUCT_OUT/data/app/Sample/Sample.apk.
        file = new File(getApkDir(), Files.getNameWithoutExtension(filename) + "/" + filename);
        if (file.exists()) {
            return file;
        }
        return null;
    }

    private void initApkList() {
        if (mApks != null) {
            return;
        }

        // Find an apk info file.  The priorities are:
        // 1. Use the specified apk-info if available.
        // 2. Use 'apk-info.xml' if there is one in the apk-dir directory.
        // 3. Use the default apk-info.xml in res.
        if (mApkInfoFileName != null) {
            mApkInfoFile = new File(mApkInfoFileName);
        } else {
            mApkInfoFile = new File(getApkDir(), "apk-info.xml");

            if (!mApkInfoFile.exists()) {
                String resource = "/com/android/game/qualification/apk-info.xml";
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
        }
        ApkListXmlParser parser = new ApkListXmlParser();
        try {
            mApks = parser.parse(mApkInfoFile);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    /** Check if an image is black. */
    @VisibleForTesting
    static boolean isImageBlack(InputStream stream) throws IOException {
        BufferedImage img = ImageIO.read(stream);
        for (int i = 0; i < img.getWidth(); i++) {
            // Only check the middle portion of the image to avoid status bar.
            for (int j = img.getHeight() / 4; j < img.getHeight() * 3 / 4; j++) {
                int color = img.getRGB(i, j);
                // Check if pixel is non-black and not fully transparent.
                if ((color & 0x00ffffff) != 0 && (color >> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private void checkScreenshot(ITestInvocationListener listener, TestDescription testId)
            throws DeviceNotAvailableException {
        try (InputStreamSource screenSource = mDevice.getScreenshot()) {
            listener.testLog(String.format(
                    "screenshot-%s",
                    testId.getTestName()),
                    LogDataType.PNG,
                    screenSource);
            try (InputStream stream = screenSource.createInputStream()) {
                stream.reset();
                if (isImageBlack(stream)) {
                    listener.testFailed(testId, "Screenshot was all black.");
                }
            }
        } catch (IOException e) {
            listener.testFailed(
                    testId, "Failed reading screenshot data:\n" + e.getMessage());
        }
    }

    // TODO: Migrate to use BaseHostJUnit4Test when possible.  It is not currently possible because
    // the IInvocationContext is private.
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
