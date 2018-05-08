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
package com.android.graphics.benchmark.device;

import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.android.graphics.benchmark.proto.ResultDataProto;
import com.android.graphics.benchmark.ResultData;

public class MetricsReporter {
    private String appName;
    private List<Long> loopStartTimesMsecs = new ArrayList<>();

    public void begin(String appName) {
        this.appName = appName;
    }

    public void startLoop(long timestampNsecs) {
        loopStartTimesMsecs.add(timestampNsecs);
    }

    public void end() throws IOException {
        File file = new File("/sdcard/" + ResultData.RESULT_FILE_LOCATION);
        Files.deleteIfExists(file.toPath());
        ResultDataProto.Result.Builder builder = ResultDataProto.Result.newBuilder();

        for (Long value : loopStartTimesMsecs) {
            builder.addEvents(ResultDataProto.Event.newBuilder()
                    .setType(ResultDataProto.Event.Type.START_LOOP)
                    .setTimestamp(value)
                    .build());
        }

        try (OutputStream outputStream = new FileOutputStream(file)) {
            builder.build().writeTo(outputStream);
        }
    }
}
