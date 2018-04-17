/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "android_benchmark.h"
#include <jni.h>
#include <android/log.h>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "AndroidGraphicsBenchmark", __VA_ARGS__))

static JavaVM* sJavaVm = nullptr;
static JNIEnv* sJniEnv = nullptr;

// Initialize sJavaVm if the app uses JNI.
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    sJavaVm = vm;
    sJavaVm->GetEnv(reinterpret_cast<void**>(&sJniEnv), JNI_VERSION_1_6);
    return sJniEnv->GetVersion();
}

namespace android {

static const char* const INTENT_START = "com.android.graphics.benchmark.START";

static JNIEnv* getJniEnv() {
    if (sJniEnv == nullptr) {
        sJavaVm->AttachCurrentThread(&sJniEnv, nullptr);
    }
    return sJniEnv;
}

// Create an Intent jobject in Java.
static jobject createIntent() {
    JNIEnv* env = getJniEnv();
    jclass intentClass = env->FindClass("android/content/Intent");
    jmethodID constructor = env->GetMethodID(intentClass, "<init>", "(Ljava/lang/String;)V");
    jobject intent = env->NewObject(intentClass, constructor, env->NewStringUTF(INTENT_START));
    jmethodID set_type =
            env->GetMethodID(
                intentClass,
                "setType",
                "(Ljava/lang/String;)Landroid/content/Intent;");
    env->CallObjectMethod(intent, set_type, env->NewStringUTF("text/plain"));
    return intent;
}

// Implementation of AndroidGraphicsBenchmark::Impl.

class AndroidGraphicsBenchmark::Impl {
public:
    void startBenchmark(jobject context);
    void startBenchmark(ANativeActivity* activity);
};


void AndroidGraphicsBenchmark::Impl::startBenchmark(jobject context) {
    LOGI("startBenchmark");
    JNIEnv* env = getJniEnv();
    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID method =
            env->GetMethodID(
                contextClass,
                "sendBroadcast",
                "(Landroid/content/Intent;)V");
    env->CallVoidMethod(context, method, createIntent());
}

void AndroidGraphicsBenchmark::Impl::startBenchmark(ANativeActivity* activity) {
    sJavaVm = activity->vm;
    startBenchmark(activity->clazz);
}

/* Implementation of AndroidGraphicsBenchmark */

AndroidGraphicsBenchmark::AndroidGraphicsBenchmark() {
    mImpl = new AndroidGraphicsBenchmark::Impl();
}

AndroidGraphicsBenchmark::~AndroidGraphicsBenchmark() {
    delete mImpl;
}

void AndroidGraphicsBenchmark::startBenchmark(jobject context) {
    mImpl->startBenchmark(context);
}

void AndroidGraphicsBenchmark::startBenchmark(ANativeActivity* activity) {
    mImpl->startBenchmark(activity);
}

} // end of namespace android
