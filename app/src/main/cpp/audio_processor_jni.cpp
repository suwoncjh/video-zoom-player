#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <vector>

namespace {

constexpr int kTargetSampleRateHz = 48000;
constexpr int kTargetChannelCount = 3;
constexpr int kSamplesPer20ms = 960;

struct ProcessorState {
  int sample_rate_hz = kTargetSampleRateHz;
  int channel_count = kTargetChannelCount;
};

ProcessorState* AsState(jlong handle) {
  return reinterpret_cast<ProcessorState*>(handle);
}

// Expected external API from your DSP library.
// `length` is sample-frames per channel (fixed 960 at 48kHz for 20 ms).
extern "C" void process(int* out, const int* in, int length) __attribute__((weak));

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
  const int samples_per_channel = state->channel_count > 0 ? input_length / state->channel_count : 0;
  const bool valid_block =
      state->sample_rate_hz == kTargetSampleRateHz &&
      state->channel_count == kTargetChannelCount &&
      samples_per_channel == kSamplesPer20ms &&
      input_length == kSamplesPer20ms * kTargetChannelCount;

  std::vector<jint> input(static_cast<size_t>(input_length));
  env->GetIntArrayRegion(input_int32, 0, input_length, input.data());

  std::vector<jint> output(input);

  if (valid_block && process != nullptr) {
    process(output.data(), input.data(), kSamplesPer20ms);
  }

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
