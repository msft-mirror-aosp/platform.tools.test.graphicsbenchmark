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

package com.android.game.qualification.test;
import org.junit.runner.RunWith;
import org.junit.Test;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.device.DeviceNotAvailableException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(DeviceJUnit4ClassRunner.class)
public class FunctionalTests extends BaseHostJUnit4Test {

    private final String GOOGLE_DISPLAY_TIMING_EXTENSION_NAME = "VK_GOOGLE_display_timing";

    @Test
    public void testExposesDisplayTimingExtension()
        throws DeviceNotAvailableException {
        String vulkanCapabilities = getDevice().executeShellCommand("cmd gpu vkjson");

        assertTrue(vulkanCapabilities.contains(GOOGLE_DISPLAY_TIMING_EXTENSION_NAME));
    }

}
