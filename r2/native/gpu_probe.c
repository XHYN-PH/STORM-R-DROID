/* Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT */
#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <vulkan/vulkan.h>

/* Inspect the system ICD once in the launcher process; no window, surface or engine state. */
JNIEXPORT jstring JNICALL Java_com_naruto_r2_DriverSupport_systemGpu(JNIEnv *env, jclass type) {
    (void)type;
    char result[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE + 32] = "";
    void *module = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (module) {
        PFN_vkGetInstanceProcAddr get = (PFN_vkGetInstanceProcAddr)dlsym(module, "vkGetInstanceProcAddr");
        PFN_vkCreateInstance create = get ? (PFN_vkCreateInstance)get(VK_NULL_HANDLE, "vkCreateInstance") : NULL;
        VkApplicationInfo application = {.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO,
            .pApplicationName="STORM R system GPU probe", .apiVersion=VK_API_VERSION_1_0};
        VkInstanceCreateInfo info = {.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo=&application};
        VkInstance instance = VK_NULL_HANDLE;
        if (create && create(&info, NULL, &instance) == VK_SUCCESS) {
            PFN_vkDestroyInstance destroy = (PFN_vkDestroyInstance)get(instance, "vkDestroyInstance");
            PFN_vkEnumeratePhysicalDevices enumerate = (PFN_vkEnumeratePhysicalDevices)get(instance, "vkEnumeratePhysicalDevices");
            PFN_vkGetPhysicalDeviceProperties properties = (PFN_vkGetPhysicalDeviceProperties)get(instance, "vkGetPhysicalDeviceProperties");
            uint32_t count = 0;
            VkPhysicalDevice devices[16];
            if (enumerate && properties && enumerate(instance, &count, NULL) == VK_SUCCESS && count > 0 && count <= 16 &&
                enumerate(instance, &count, devices) == VK_SUCCESS && count > 0) {
                VkPhysicalDeviceProperties p;
                properties(devices[0], &p);
                p.deviceName[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE-1] = '\0';
                snprintf(result, sizeof(result), "%04x|%s", p.vendorID, p.deviceName);
            }
            if (destroy) destroy(instance, NULL);
        }
        dlclose(module);
    }
    return (*env)->NewStringUTF(env, result);
}
