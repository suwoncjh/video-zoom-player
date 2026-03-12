#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <vector>

namespace {

struct ProcessorState {
  int sample_rate_hz = 48000;
  int channel_count = 3;
};

ProcessorState* AsState(jlong handle) {
  return reinterpret_cast<ProcessorState*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_videozoomplayer_NativePcmProcessor_nativeCreate(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  return reinterpret_cast<jlong>(new ProcessorState());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_videozoomplayer_NativePcmProcessor_nativeReset(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint sample_rate_hz, jint channel_count) {
  ProcessorState* state = AsState(handle);
  if (state == nullptr) {
    return;
  }
  state->sample_rate_hz = sample_rate_hz;
  state->channel_count = channel_count;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_videozoomplayer_NativePcmProcessor_nativeProcess20ms(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jintArray input_int32,
    jint sample_rate_hz,
    jint channel_count) {
  ProcessorState* state = AsState(handle);
  if (state == nullptr || input_int32 == nullptr) {
    return env->NewIntArray(0);
  }

  state->sample_rate_hz = sample_rate_hz;
  state->channel_count = channel_count;

  const jsize input_length = env->GetArrayLength(input_int32);
  std::vector<jint> input(static_cast<size_t>(input_length));
  env->GetIntArrayRegion(input_int32, 0, input_length, input.data());

  std::vector<jint> output(input);

  // Replace this section with your real C++ 20 ms processor call.
  // Expected I/O: interleaved int32 PCM, usually 48 kHz / 3 channels.
  // Example:
  // external_processor.Process20ms(input.data(), output.data(), state->channel_count);

  jintArray result = env->NewIntArray(input_length);
  if (result != nullptr) {
    env->SetIntArrayRegion(result, 0, input_length, output.data());
  }
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_videozoomplayer_NativePcmProcessor_nativeRelease(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
  ProcessorState* state = AsState(handle);
  delete state;
}

