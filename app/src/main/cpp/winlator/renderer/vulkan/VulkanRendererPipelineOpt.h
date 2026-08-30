#pragma once

#include <vulkan/vulkan.h>

class VulkanRendererContext;

VkPipeline VulkanRendererGetOpaquePipeline(VulkanRendererContext* ctx);

void VulkanRendererDestroyOpaquePipeline(VulkanRendererContext* ctx);
