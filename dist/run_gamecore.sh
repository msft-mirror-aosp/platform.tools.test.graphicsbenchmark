#!/bin/sh
ANDROID_TARGET_OUT_TESTCASES=$PWD/testcases
./bin/tradefed.sh run commandAndExit AndroidTest.xml "$@"
