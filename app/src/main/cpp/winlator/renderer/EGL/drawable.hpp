#pragma once

#include <memory>
#include <mutex>
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>

#include "renderer_jni.hpp"

struct ComposerTexture {
    bool sizeChanged;
    AHardwareBuffer *srcBuffer;
    VkImage srcImage;
    VkImageView srcImageView;
    VkDeviceMemory srcMemory;
    AHardwareBuffer *dstBuffer;
    VkImage dstImage;
    VkImageView dstImageView;
    VkDeviceMemory dstMemory;
    VkImageLayout srcImageLayout;
    VkPipelineStageFlagBits srcPipelineStage;
    VkAccessFlagBits srcAccessFlags;
    VkImageLayout dstImageLayout;
    VkPipelineStageFlagBits dstPipelineStage;
    VkAccessFlagBits dstAccessFlags;
    VkDescriptorSet vkDescriptorSet;
};

struct Drawable {
    int id;
    int width;
    int format;
    int height;
    int stride;
    int textureId;
    std::unique_ptr<ComposerTexture> composerTexture;
    bool isDirty;
    bool sizeChanged;
    bool isDirectContent;
    bool isDisplayX = false;
    int syncFence = -1;
    void *data;
    jobject drawableObj;
    AHardwareBuffer *ahb;
};
