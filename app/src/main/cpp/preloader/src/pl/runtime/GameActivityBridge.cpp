#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <link.h>
#include <mutex>
#include <string>

namespace {
constexpr const char* kTag = "LeviGameActivity";
constexpr const char* kInitializeSymbol =
        "Java_com_google_androidgamesdk_GameActivity_initializeNativeCode";

using InitializeNativeCodeFn = jlong (*) (
        JNIEnv*, jobject, jstring, jstring, jstring, jobject, jbyteArray, jobject);

InitializeNativeCodeFn gInitializeNativeCode = nullptr;
std::once_flag gResolveOnce;

int findMinecraftRuntime(struct dl_phdr_info* info, size_t, void*) {
    if (info == nullptr || info->dlpi_name == nullptr) return 0;
    std::string path(info->dlpi_name);
    if (path.find("libminecraftpe.so") == std::string::npos) return 0;

    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_NOLOAD);
    if (handle == nullptr) return 0;
    void* symbol = dlsym(handle, kInitializeSymbol);
    if (symbol != nullptr) {
        gInitializeNativeCode = reinterpret_cast<InitializeNativeCodeFn>(symbol);
        __android_log_print(ANDROID_LOG_INFO, kTag,
                "Forwarding GameActivity.initializeNativeCode to %s", path.c_str());
        return 1;
    }
    return 0;
}

InitializeNativeCodeFn resolveMinecraftInitializer() {
    std::call_once(gResolveOnce, [] {
        dl_iterate_phdr(findMinecraftRuntime, nullptr);
    });
    return gInitializeNativeCode;
}
} // namespace

/**
 * MinecraftActivity selects libpreloader.so through android.app.lib_name. The
 * Android Games Activity class consequently resolves its native method here,
 * while the implementation itself lives in the extracted Minecraft runtime.
 * Forwarding preserves the original Minecraft GameActivity initialization.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_google_androidgamesdk_GameActivity_initializeNativeCode(
        JNIEnv* env,
        jobject activity,
        jstring internalDataPath,
        jstring obbPath,
        jstring externalDataPath,
        jobject assetManager,
        jbyteArray savedState,
        jobject configuration) __attribute__((visibility("default")));

extern "C" JNIEXPORT jlong JNICALL
Java_com_google_androidgamesdk_GameActivity_initializeNativeCode(
        JNIEnv* env,
        jobject activity,
        jstring internalDataPath,
        jstring obbPath,
        jstring externalDataPath,
        jobject assetManager,
        jbyteArray savedState,
        jobject configuration) {
    InitializeNativeCodeFn initializer = resolveMinecraftInitializer();
    if (initializer == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                "libminecraftpe.so does not expose GameActivity.initializeNativeCode");
        jclass errorClass = env->FindClass("java/lang/IllegalStateException");
        if (errorClass != nullptr) {
            env->ThrowNew(errorClass,
                    "Minecraft runtime GameActivity bridge is unavailable. Reinstall this Minecraft instance.");
        }
        return 0;
    }
    return initializer(env, activity, internalDataPath, obbPath, externalDataPath,
            assetManager, savedState, configuration);
}
