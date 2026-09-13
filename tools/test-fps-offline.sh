#!/usr/bin/env bash
# Runs only the Android-free FPS policy tests using Kotlin/JUnit bundled with Gradle 8.13.
# Does not compile an APK or prove Camera2 / GPU compatibility on any device.
set -euo pipefail
fps_lib="${1:?Usage: bash tools/test-fps-offline.sh /path/to/gradle-8.13/lib [java-binary]}"
fps_java="${2:-java}"
fps_project="$(cd "$(dirname "$0")/.." && pwd)"
cd "$fps_project"
fps_classpath="$fps_lib/kotlin-stdlib-2.0.21.jar:$fps_lib/junit-4.13.2.jar:$fps_lib/hamcrest-core-1.3.jar"
mkdir -p build/fps-logic-tests
fps_sources=()
for fps_file in StreamConfig CameraMode DirectSensorControl FrameDelivery; do
  fps_sources+=("app/src/main/java/com/vangnang/youtubelive/$fps_file.kt")
done
fps_tests=(CameraModeTest DirectSensorControlTest FrameDeliveryTest CameraRetryTest StreamConfigTest)
fps_test_classes=()
for fps_test in "${fps_tests[@]}"; do
  fps_sources+=("app/src/test/java/com/vangnang/youtubelive/$fps_test.kt")
  fps_test_classes+=("com.vangnang.youtubelive.$fps_test")
done
"$fps_java" -cp "$fps_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$fps_classpath" \
  -d build/fps-logic-tests "${fps_sources[@]}"
cd app
"$fps_java" -cp "../build/fps-logic-tests:$fps_classpath" org.junit.runner.JUnitCore "${fps_test_classes[@]}"
