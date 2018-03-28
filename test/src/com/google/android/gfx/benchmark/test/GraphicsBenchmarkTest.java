package com.google.android.gfx.benchmark.test;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Assert;
import java.io.IOException;
import java.lang.InterruptedException;
import android.util.Log;
import android.support.test.InstrumentationRegistry;
import android.content.Intent;

@RunWith(JUnit4.class)
public final class GraphicsBenchmarkTest {
    private static final String TAG = "GraphicsBenchmarkTest";

    private enum App {
        SNIPER_3D("com.fungames.sniper3d"),
        AFTERPULSE("com.dle.afterpulse");

        private String packageName;

        App(String packageName) {
            this.packageName = packageName;
        }

        String getPackageName() {
            return packageName;
        }
    }

    @Before public void setUp() {
    }

    @Test public void testSniper3d() throws IOException, InterruptedException {
        startApp(App.SNIPER_3D);
    }

    @Test public void testAfterpulse() throws IOException, InterruptedException {
        startApp(App.AFTERPULSE);
    }

    private void startApp(App app) throws IOException, InterruptedException {
        Log.d(TAG, "Launching " + app.getPackageName());

        // TODO: Need to support passing arguments to intents.
        Intent intent =
                InstrumentationRegistry.getContext().getPackageManager()
                    .getLaunchIntentForPackage(app.getPackageName());
        InstrumentationRegistry.getContext().startActivity(intent);
        Thread.sleep(10000);
    }
}
