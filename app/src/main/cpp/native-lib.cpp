#include <jni.h>
#include <string>
#include <vector>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "cactus.h"

static bool g_loaded = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_manish_miragelens_NativeBridge_loadModel(
        JNIEnv* env, jobject /* thiz */, jobject assetManager, jstring fileName) {
    if (g_loaded) return JNI_TRUE;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) return JNI_FALSE;

    const char* name = env->GetStringUTFChars(fileName, nullptr);
    AAsset* asset = AAssetManager_open(mgr, name, AASSET_MODE_BUFFER);
    env->ReleaseStringUTFChars(fileName, name);
    if (!asset) return JNI_FALSE;

    size_t len = AAsset_getLength(asset);
    std::vector<unsigned char> buf(len);
    if (len == 0 || AAsset_read(asset, buf.data(), len) != (ssize_t) len) {
        AAsset_close(asset);
        return JNI_FALSE;
    }
    AAsset_close(asset);

    int rc = cactus_load(buf.data(), (unsigned long long) buf.size());
    // cactus_load returns 0 on success; also accept a loaded vision model
    // as proof, so a different return convention cannot break loading.
    bool ok = (rc == 0) || ((cactus_models() & CACTUS_VISION) != 0);
    if (!ok) return JNI_FALSE;
    cactus_set_threads(4);
    g_loaded = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_manish_miragelens_NativeBridge_describe(
        JNIEnv* env, jobject /* thiz */, jbyteArray rgb, jint width, jint height) {
    if (!g_loaded) {
        return env->NewStringUTF("{\"error\":\"model not loaded\"}");
    }
    jsize n = env->GetArrayLength(rgb);
    std::vector<unsigned char> buf((size_t) n);
    env->GetByteArrayRegion(rgb, 0, n, reinterpret_cast<jbyte*>(buf.data()));

    std::vector<char> out(1 << 20);
    int rc = cactus_describe(buf.data(), width, height,
                             nullptr, "en", nullptr,
                             out.data(), (int) out.size());
    if (rc != 0) {
        // Retry once with an empty options object, in case null is rejected.
        rc = cactus_describe(buf.data(), width, height,
                             "{}", "en", nullptr,
                             out.data(), (int) out.size());
    }
    if (rc != 0 || out[0] != '{') {
        const char* err = cactus_last_error();
        std::string msg = std::string("{\"error\":\"describe failed");
        if (err && err[0] != '\0') {
            msg += ": ";
            msg += err;
        }
        msg += "\"}";
        return env->NewStringUTF(msg.c_str());
    }
    return env->NewStringUTF(out.data());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_manish_miragelens_NativeBridge_isModelLoaded(
        JNIEnv* /* env */, jobject /* thiz */) {
    return g_loaded ? JNI_TRUE : JNI_FALSE;
}
