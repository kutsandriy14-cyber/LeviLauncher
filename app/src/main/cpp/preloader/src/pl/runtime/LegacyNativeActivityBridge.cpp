#include <android/log.h>
#include <android/native_activity.h>
#include <dlfcn.h>
#include <link.h>
#include <mutex>
#include <string>

namespace {
constexpr const char* kTag = "LeviNativeActivity";
constexpr const char* kEntryPoint = "ANativeActivity_onCreate";
using NativeActivityCreateFn = void (*)(ANativeActivity*, void*, size_t);

NativeActivityCreateFn gMinecraftCreate = nullptr;
std::once_flag gResolveOnce;

int findMinecraftNativeActivity(struct dl_phdr_info* info, size_t, void*) {
    if (info == nullptr || info->dlpi_name == nullptr) return 0;
    const std::string path(info->dlpi_name);
    if (path.find("libminecraftpe.so") == std::string::npos) return 0;

    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_NOLOAD);
    if (handle == nullptr) return 0;
    void* symbol = dlsym(handle, kEntryPoint);
    if (symbol == nullptr) return 0;

    gMinecraftCreate = reinterpret_cast<NativeActivityCreateFn>(symbol);
    __android_log_print(ANDROID_LOG_INFO, kTag,
            "Forwarding NativeActivity creation to %s", path.c_str());
    return 1;
}

NativeActivityCreateFn resolveMinecraftEntry() {
    std::call_once(gResolveOnce, [] {
        dl_iterate_phdr(findMinecraftNativeActivity, nullptr);
    });
    return gMinecraftCreate;
}
} // namespace

/**
 * Legacy Minecraft packages declare android.app.lib_name=minecraftpe and
 * export ANativeActivity_onCreate. LeviLauncher uses libpreloader.so as the
 * Android activity library, so this bridge restores the original handoff.
 */
extern "C" __attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity* activity, void* savedState, size_t savedStateSize) {
    NativeActivityCreateFn minecraftCreate = resolveMinecraftEntry();
    if (minecraftCreate == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                "Minecraft NativeActivity entrypoint is unavailable");
        return;
    }
    minecraftCreate(activity, savedState, savedStateSize);
}
