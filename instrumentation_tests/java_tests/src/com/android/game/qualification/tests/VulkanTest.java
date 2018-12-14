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

import static junit.framework.Assert.assertTrue;

import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VulkanTest {
    private static final int VULKAN_1_1 = 0x00401000; // 1.1.0

    @Test
    public void support1_1() {
        PackageManager pm = InstrumentationRegistry.getTargetContext().getPackageManager();
        assertTrue(
                "Device must support Vulkan 1.1",
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1));
    }
}

