package com.manish.miragelens

import android.content.res.AssetManager

/** JNI bridge to the Cactus engine (libcactus.a) + Mirage weights. */
object NativeBridge {
    init {
        System.loadLibrary("miragelens")
    }

    /** Loads mirage.cact from the app assets. Returns true when ready. */
    external fun loadModel(assetManager: AssetManager, fileName: String): Boolean

    /**
     * Runs Mirage on raw RGB pixels (width * height * 3 bytes).
     * Returns the JSON result string, or a JSON object with an "error" key.
     */
    external fun describe(rgb: ByteArray, width: Int, height: Int): String

    external fun isModelLoaded(): Boolean
}
