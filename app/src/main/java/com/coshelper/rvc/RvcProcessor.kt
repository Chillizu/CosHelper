package com.coshelper.rvc

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class RvcProcessor {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    // 80 ms block, 20 ms overlap -> 60 ms hop
    private val blockSize = 1280
    private val overlap = 320

    // Accumulated input samples
    private var inputBuffer = ShortArray(0)
    private var lastOutputOverlap = ShortArray(0)

    fun loadModel(path: String) {
        unload()
        env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            addNnapi() // use NNAPI if available
            setIntraOpNumThreads(4)
        }
        session = env?.createSession(path, options)
    }

    fun unload() {
        session?.close()
        session = null
        inputBuffer = ShortArray(0)
        lastOutputOverlap = ShortArray(0)
    }

    fun process(pcm: ShortArray): ShortArray? {
        val sess = session ?: return null
        val ortEnv = env ?: return null

        inputBuffer = inputBuffer + pcm
        if (inputBuffer.size < blockSize) return null

        val block = inputBuffer.copyOf(blockSize)
        inputBuffer = inputBuffer.copyOfRange(blockSize - overlap, inputBuffer.size)

        val floats = FloatArray(blockSize) { i -> block[i] / 32768.0f }

        val inputTensor = try {
            OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floats), longArrayOf(1, blockSize.toLong()))
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        inputTensor.use {
            val outputs = sess.run(mapOf(getInputName() to inputTensor))
            outputs.use {
                val resultTensor = outputs[0].value as? Array<*> ?: return null
                val outputFloats = (resultTensor[0] as? FloatArray) ?: return null

                val outputBlock = ShortArray(blockSize) { i ->
                    val v = outputFloats.getOrElse(i) { 0.0f }
                    (max(-1.0f, min(1.0f, v)) * 32767.0f).toInt().toShort()
                }

                // Cross-fade overlap region with previous block's tail
                val fused = ShortArray(blockSize)
                for (i in 0 until overlap) {
                    val a = lastOutputOverlap.getOrElse(i) { 0 }.toFloat() / 32768.0f
                    val b = outputBlock[i].toFloat() / 32768.0f
                    val ratio = i / overlap.toFloat()
                    val mixed = a * (1 - ratio) + b * ratio
                    fused[i] = (max(-32768f, min(32767f, mixed * 32767f))).toInt().toShort()
                }
                for (i in overlap until blockSize) {
                    fused[i] = outputBlock[i]
                }
                lastOutputOverlap = outputBlock.copyOfRange(blockSize - overlap, blockSize)
                return fused
            }
        }
    }

    private fun getInputName(): String {
        return session?.inputNames?.firstOrNull() ?: "input"
    }
}
