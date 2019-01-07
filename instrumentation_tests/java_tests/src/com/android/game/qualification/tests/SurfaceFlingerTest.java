package com.android.game.qualification.tests;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests related to the surface flinger.
 */
@RunWith(AndroidJUnit4.class)
public class SurfaceFlingerTest {

    @Rule
    public final ActivityTestRule<SurfaceFlingerTestActivity> mActivityRule =
            new ActivityTestRule<>(SurfaceFlingerTestActivity.class);

    /**
     * Check surface flinger does not latch a frame before the GPU work is completed.
     *
     * Latching a frame before GPU work is completed can lead to unpredictable delays in the HWC
     * that is impossible to detect in the app.
     */
    @Test
    public void latchAfterReady() throws InterruptedException {
        // Let the activity run for a few seconds.
        Thread.sleep(3000);
        SurfaceFlingerTestActivity activity = mActivityRule.getActivity();
        mActivityRule.finishActivity();

        Long[] readyTimes = activity.getReadyTimes().toArray(new Long[0]);
        Long[] latchTimes = activity.getLatchTimes().toArray(new Long[0]);

        assertWithMessage("Unable to retrieve frame ready time.")
                .that(readyTimes)
                .isNotEmpty();

        assertWithMessage("Unable to retrieve buffer latch time.")
                .that(latchTimes)
                .isNotEmpty();

        for (int i = 0; i < readyTimes.length; i++) {
            // Check all frame ready time is before latch time.  Add a slight (0.1ms) tolerance
            // because the latch time is slightly before the actual condition check in the
            // surface flinger.
            assertWithMessage(
                    "SurfaceFlinger must latch after GPU work is completed for the frame")
                    .that(latchTimes[i])
                    .named("latch time")
                    .isGreaterThan(readyTimes[i] - 100_000);
        }
    }
}
