#include <jni.h>
#include <cstring>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static whisper_context *g_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_coshelper_stt_WhisperJNI_loadModel(JNIEnv *env, jobject /* thiz */, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
    g_ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    env->ReleaseStringUTFChars(modelPath, path);
    if (g_ctx == nullptr) {
        LOGE("Failed to load model from file");
        return false;
    }
    LOGD("Model loaded successfully");
    return true;
}

JNIEXPORT jstring JNICALL
Java_com_coshelper_stt_WhisperJNI_transcribe(JNIEnv *env, jobject /* thiz */, jfloatArray samples, jint sampleCount, jstring language) {
    if (g_ctx == nullptr) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    const char *lang = env->GetStringUTFChars(language, nullptr);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;
    params.translate = false;
    params.n_threads = 4;
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    jfloat *samplesPtr = env->GetFloatArrayElements(samples, nullptr);
    int ret = whisper_full(g_ctx, params, samplesPtr, sampleCount);
    env->ReleaseFloatArrayElements(samples, samplesPtr, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);

    if (ret != 0) {
        LOGE("whisper_full failed: %d", ret);
        return env->NewStringUTF("");
    }

    int n_segments = whisper_full_n_segments(g_ctx);
    std::string result;
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(g_ctx, i);
        if (text) {
            result += text;
        }
    }
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_coshelper_stt_WhisperJNI_freeModel(JNIEnv * /* env */, jobject /* thiz */) {
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}

} // extern "C"
