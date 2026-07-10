package com.coshelper.stt

class WhisperJNI {
    external fun loadModel(modelPath: String): Boolean
    external fun transcribe(samples: FloatArray, sampleCount: Int, language: String): String
    external fun freeModel()

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }
}
