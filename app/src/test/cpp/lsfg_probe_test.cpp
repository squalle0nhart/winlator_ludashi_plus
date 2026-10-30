#include "lsfg_probe.h"
#include "probe_vk_table.h"
#include <cassert>
#include <cstring>

namespace {
uint32_t apiVersion = VK_API_VERSION_1_1;
bool memoryModel = true;
void VKAPI_PTR properties(VkPhysicalDevice, VkPhysicalDeviceProperties* props) {
    props->apiVersion = apiVersion;
}
void VKAPI_PTR features(VkPhysicalDevice, VkPhysicalDeviceFeatures2* f) {
    f->features.shaderStorageImageWriteWithoutFormat = VK_TRUE;
    f->features.shaderStorageImageExtendedFormats = VK_TRUE;
    auto* header = static_cast<VkBaseOutStructure*>(f->pNext);
    if (header->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES) {
        auto* v12 = static_cast<VkPhysicalDeviceVulkan12Features*>(f->pNext);
        v12->vulkanMemoryModel = memoryModel;
    } else {
        assert(header->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES_KHR);
        auto* mm = static_cast<VkPhysicalDeviceVulkanMemoryModelFeaturesKHR*>(f->pNext);
        mm->vulkanMemoryModel = memoryModel;
    }
}
}

void runProbeChecks() {
    VkTable vk{properties, features, nullptr};
    auto pd = reinterpret_cast<VkPhysicalDevice>(1);
    std::vector<VkExtensionProperties> extensions(3);
    const char* names[] = {VK_KHR_SPIRV_1_4_EXTENSION_NAME,
        VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME, VK_KHR_VULKAN_MEMORY_MODEL_EXTENSION_NAME};
    for (size_t i = 0; i < extensions.size(); ++i)
        std::strcpy(extensions[i].extensionName, names[i]);
    assert(!lsfg::queryFeatures(vk, pd, extensions, false).deviceGatesPass());
    auto compat = lsfg::queryFeatures(vk, pd, extensions, true);
    assert(compat.deviceGatesPass() && compat.spirvTarget == lsfg::kSpirv14);
    assert(lsfg::extensionNames(compat).size() == 3);
    for (size_t i = 0; i < extensions.size(); ++i) {
        auto missing = extensions;
        missing.erase(missing.begin() + i);
        assert(!lsfg::queryFeatures(vk, pd, missing, true).deviceGatesPass());
    }
    memoryModel = false;
    assert(!lsfg::queryFeatures(vk, pd, extensions, true).deviceGatesPass());
    memoryModel = true;
    apiVersion = VK_API_VERSION_1_2;
    assert(!lsfg::queryFeatures(vk, pd, {}, false).deviceGatesPass());
    compat = lsfg::queryFeatures(vk, pd, {}, true);
    assert(compat.deviceGatesPass() && compat.spirvTarget == lsfg::kSpirv15);
    assert(lsfg::extensionNames(compat).empty());
    apiVersion = VK_API_VERSION_1_3;
    auto modern = lsfg::queryFeatures(vk, pd, {}, false);
    assert(modern.deviceGatesPass() && modern.spirvTarget == lsfg::kSpirv16);
    vk.GetPhysicalDeviceFeatures2 = nullptr;
    assert(!lsfg::queryFeatures(vk, pd, extensions, true).deviceGatesPass());
}
