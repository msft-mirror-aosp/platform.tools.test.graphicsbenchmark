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
package com.android.game.qualification.tests;

import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure the device is in the correct state.
 */
@RunWith(AndroidJUnit4.class)
public class DeviceStateTest {

    /**
     * Airplane mode should be enabled during certification tests.
     */
    @Test
    public void isAirplaneModeOn() throws Settings.SettingNotFoundException {
        Assert.assertEquals(
                "Device must be in airplane mode for certification.",
                1,
                Settings.Global.getInt(
                        InstrumentationRegistry.getTargetContext().getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON));
    }
}
