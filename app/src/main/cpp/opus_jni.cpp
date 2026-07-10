#include <jni.h>
#include <cstring>
#include <opus.h>
#include <android/log.h>

#define LOG_TAG "OpusJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const int SAMPLE_RATE = 16000;
static const int CHANNELS = 1;
static OpusEncoder *g_encoder = nullptr;
static OpusDecoder *g_decoder = nullptr;

static bool initEncoder() {
    if (g_encoder) return true;
    int error = 0;
    g_encoder = opus_encoder_create(SAMPLE_RATE, CHANNELS, OPUS_APPLICATION_AUDIO, &error);
    if (error != OPUS_OK || g_encoder == nullptr) {
        LOGE("opus_encoder_create failed: %d", error);
        return false;
    }
    opus_encoder_ctl(g_encoder, OPUS_SET_BITRATE(24000));
    opus_encoder_ctl(g_encoder, OPUS_SET_COMPLEXITY(5));
    opus_encoder_ctl(g_encoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
    return true;
}

static bool initDecoder() {
    if (g_decoder) return true;
    int error = 0;
    g_decoder = opus_decoder_create(SAMPLE_RATE, CHANNELS, &error);
    if (error != OPUS_OK || g_decoder == nullptr) {
        LOGE("opus_decoder_create failed: %d", error);
        return false;
    }
    return true;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_coshelper_audio_OpusCodec_nativeEncode(JNIEnv *env, jobject /*thiz*/, jshortArray samples, jint sampleCount, jbyteArray output, jint maxBytes) {
    if (!initEncoder()) return -1;
    if (samples == nullptr || output == nullptr) return -1;
    jsize sampleLen = env->GetArrayLength(samples);
    jsize outputLen = env->GetArrayLength(output);
    if (sampleCount < 0 || sampleCount > sampleLen || maxBytes < 0 || maxBytes > outputLen) {
        LOGE("nativeEncode: invalid lengths sampleCount=%d/%d maxBytes=%d/%d", sampleCount, sampleLen, maxBytes, outputLen);
        return -1;
    }
    jshort *pcm = env->GetShortArrayElements(samples, nullptr);
    jbyte *data = env->GetByteArrayElements(output, nullptr);
    int len = opus_encode(g_encoder, pcm, sampleCount, reinterpret_cast<unsigned char *>(data), maxBytes);
    env->ReleaseShortArrayElements(samples, pcm, JNI_ABORT);
    env->ReleaseByteArrayElements(output, data, 0);
    if (len < 0) {
        LOGE("opus_encode failed: %d", len);
    }
    return len;
}

JNIEXPORT jint JNICALL
Java_com_coshelper_audio_OpusCodec_nativeDecode(JNIEnv *env, jobject /*thiz*/, jbyteArray data, jint dataLen, jshortArray output, jint sampleCount) {
    if (!initDecoder()) return -1;
    if (data == nullptr || output == nullptr) return -1;
    jsize dataArrayLen = env->GetArrayLength(data);
    jsize outputArrayLen = env->GetArrayLength(output);
    if (dataLen < 0 || dataLen > dataArrayLen || sampleCount < 0 || sampleCount > outputArrayLen) {
        LOGE("nativeDecode: invalid lengths dataLen=%d/%d sampleCount=%d/%d", dataLen, dataArrayLen, sampleCount, outputArrayLen);
        return -1;
    }
    jbyte *in = env->GetByteArrayElements(data, nullptr);
    jshort *out = env->GetShortArrayElements(output, nullptr);
    int decoded = opus_decode(g_decoder, reinterpret_cast<const unsigned char *>(in), dataLen, out, sampleCount, 0);
    env->ReleaseByteArrayElements(data, in, JNI_ABORT);
    env->ReleaseShortArrayElements(output, out, 0);
    if (decoded < 0) {
        LOGE("opus_decode failed: %d", decoded);
    }
    return decoded;
}

} // extern "C"
