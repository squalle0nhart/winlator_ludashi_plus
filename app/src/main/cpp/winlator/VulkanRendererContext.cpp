#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wmissing-field-initializers"
#include "VulkanRendererContext.h"
#include <stdexcept>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <inttypes.h>
#include <dlfcn.h>
#include "window_vert.h"
#include "window_frag.h"
#include "window_sgsr_frag.h"
#include "window_nis_frag.h"
#include "window_legacy_upscale_frag.h"
#include "window_stretch_frag.h"
#include "window_postfx_frag.h"
#include "framegen_vert.h"
#include "framegen_motion_comp.h"
#include "framegen_interpolate_frag.h"

VulkanRendererContext::VulkanRendererContext(ANativeWindow* win, int cW, int cH, void* aHandle)
    : window(win), surfaceWidth(cW), surfaceHeight(cH), containerWidth(cW), containerHeight(cH),
      adrenotoolsHandle(aHandle)
{
    createInstance(); createSurface(); pickPhysicalDevice(); createLogicalDevice();
    createSwapchain(); createRenderPass(); createDSLayout();
    createPipeline(true, pipeline);
    createFramebuffers(); createCmdPool(); createSampler();
    createWinTexPool(); createCursorDS(); createCmdBufs(); createSyncObjects();
    isRunning = true;
    renderThread = std::thread(&VulkanRendererContext::renderLoop, this);
}

VulkanRendererContext::~VulkanRendererContext() {
    isRunning = false; dirtyCV.notify_all();
    if (renderThread.joinable()) renderThread.join();
    std::lock_guard<std::mutex> lk(renderMutex);
    vk_.DeviceWaitIdle(device);
    for (auto& [id, wt] : texMap) destroyWinTex(wt);
    texMap.clear();

    for (auto& wt : deleteQueue) {
        if (wt.ds   != VK_NULL_HANDLE) vk_.FreeDescriptorSets(device, winTexPool, 1, &wt.ds);
        if (wt.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, wt.view, nullptr);
        if (wt.img  != VK_NULL_HANDLE) vk_.DestroyImage(device, wt.img, nullptr);
        if (wt.mem  != VK_NULL_HANDLE) vk_.FreeMemory(device, wt.mem, nullptr);
        if (wt.stg  != VK_NULL_HANDLE) { vk_.DestroyBuffer(device, wt.stg, nullptr); vk_.FreeMemory(device, wt.stgMem, nullptr); }
    }
    deleteQueue.clear();
    destroyFrameGenResources();
    cleanupSwapchain(); cleanupCursorTex();

    vk_.DestroySampler(device, sampler, nullptr);
    vk_.DestroyDescriptorPool(device, winTexPool, nullptr);
    if (sgsrPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, sgsrPipeline, nullptr);
    if (nisPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, nisPipeline, nullptr);
    if (legacyUpscalePipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, legacyUpscalePipeline, nullptr);
    if (stretchPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, stretchPipeline, nullptr);
    if (postfxPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, postfxPipeline, nullptr);
    if (frameGenMotionPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, frameGenMotionPipeline, nullptr);
    if (frameGenInterpPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, frameGenInterpPipeline, nullptr);
    if (frameGenMotionPipeLayout != VK_NULL_HANDLE) vk_.DestroyPipelineLayout(device, frameGenMotionPipeLayout, nullptr);
    if (frameGenInterpPipeLayout != VK_NULL_HANDLE) vk_.DestroyPipelineLayout(device, frameGenInterpPipeLayout, nullptr);
    if (frameGenMotionLayout != VK_NULL_HANDLE) vk_.DestroyDescriptorSetLayout(device, frameGenMotionLayout, nullptr);
    if (frameGenInterpLayout != VK_NULL_HANDLE) vk_.DestroyDescriptorSetLayout(device, frameGenInterpLayout, nullptr);
    if (frameGenHistoryPass != VK_NULL_HANDLE) vk_.DestroyRenderPass(device, frameGenHistoryPass, nullptr);
    vk_.DestroyPipeline(device, pipeline, nullptr);
    vk_.DestroyPipelineLayout(device, pipeLayout, nullptr);
    vk_.DestroyDescriptorSetLayout(device, dsLayout, nullptr);
    for (uint32_t i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        vk_.DestroySemaphore(device, renderDoneSems[i], nullptr);
        vk_.DestroySemaphore(device, imgAvailSems[i], nullptr);
        vk_.DestroyFence(device, inFlightFences[i], nullptr);
    }
    vk_.DestroyCommandPool(device, cmdPool, nullptr);
    vk_.DestroyRenderPass(device, renderPass, nullptr);
    vk_.DestroyDevice(device, nullptr);
    vk_.DestroySurfaceKHR(instance, surface, nullptr);
    vk_.DestroyInstance(instance, nullptr);
    if (adrenotoolsHandle) { dlclose(adrenotoolsHandle); adrenotoolsHandle = nullptr; }
}

void VulkanRendererContext::loadInstanceDispatch() {
    auto i = [&](const char* name) { return gipa ? gipa(instance, name) : nullptr; };
#define LOAD_I2(fn) vk_.fn = (PFN_vk##fn)i("vk"#fn)
    LOAD_I2(DestroyInstance);
    LOAD_I2(EnumeratePhysicalDevices);
    LOAD_I2(GetPhysicalDeviceProperties);
    LOAD_I2(GetPhysicalDeviceMemoryProperties);
    LOAD_I2(GetPhysicalDeviceSurfaceCapabilitiesKHR);
    LOAD_I2(GetPhysicalDeviceSurfaceFormatsKHR);
    LOAD_I2(GetPhysicalDeviceSurfacePresentModesKHR);
    LOAD_I2(GetPhysicalDeviceQueueFamilyProperties);
    LOAD_I2(GetPhysicalDeviceSurfaceSupportKHR);
    LOAD_I2(CreateDevice);
    LOAD_I2(DestroySurfaceKHR);
    LOAD_I2(CreateAndroidSurfaceKHR);
    LOAD_I2(GetDeviceProcAddr);
}

void VulkanRendererContext::loadDeviceDispatch() {
    auto d = [&](const char* name) -> PFN_vkVoidFunction {
        return vk_.GetDeviceProcAddr ? vk_.GetDeviceProcAddr(device, name) : nullptr;
    };
#define LOAD_D2(fn) vk_.fn = (PFN_vk##fn)d("vk"#fn)
    LOAD_D2(DestroyDevice);
    LOAD_D2(GetDeviceQueue);
    LOAD_D2(DeviceWaitIdle);
    LOAD_D2(CreateSwapchainKHR);
    LOAD_D2(DestroySwapchainKHR);
    LOAD_D2(GetSwapchainImagesKHR);
    LOAD_D2(AcquireNextImageKHR);
    LOAD_D2(QueuePresentKHR);
    LOAD_D2(QueueSubmit);
    LOAD_D2(CreateRenderPass);
    LOAD_D2(DestroyRenderPass);
    LOAD_D2(CreateFramebuffer);
    LOAD_D2(DestroyFramebuffer);
    LOAD_D2(CreateImageView);
    LOAD_D2(DestroyImageView);
    LOAD_D2(CreateImage);
    LOAD_D2(DestroyImage);
    LOAD_D2(CreateBuffer);
    LOAD_D2(DestroyBuffer);
    LOAD_D2(AllocateMemory);
    LOAD_D2(FreeMemory);
    LOAD_D2(MapMemory);
    LOAD_D2(FlushMappedMemoryRanges);
    LOAD_D2(BindBufferMemory);
    LOAD_D2(BindImageMemory);
    LOAD_D2(GetBufferMemoryRequirements);
    LOAD_D2(GetImageMemoryRequirements);
    LOAD_D2(CreateDescriptorSetLayout);
    LOAD_D2(DestroyDescriptorSetLayout);
    LOAD_D2(CreateDescriptorPool);
    LOAD_D2(DestroyDescriptorPool);
    LOAD_D2(AllocateDescriptorSets);
    LOAD_D2(FreeDescriptorSets);
    LOAD_D2(UpdateDescriptorSets);
    LOAD_D2(CreatePipelineLayout);
    LOAD_D2(DestroyPipelineLayout);
    LOAD_D2(CreateShaderModule);
    LOAD_D2(DestroyShaderModule);
    LOAD_D2(CreateGraphicsPipelines);
    LOAD_D2(CreateComputePipelines);
    LOAD_D2(DestroyPipeline);
    LOAD_D2(CreateCommandPool);
    LOAD_D2(DestroyCommandPool);
    LOAD_D2(AllocateCommandBuffers);
    LOAD_D2(FreeCommandBuffers);
    LOAD_D2(BeginCommandBuffer);
    LOAD_D2(EndCommandBuffer);
    LOAD_D2(ResetCommandBuffer);
    LOAD_D2(CmdBeginRenderPass);
    LOAD_D2(CmdEndRenderPass);
    LOAD_D2(CmdBindPipeline);
    LOAD_D2(CmdBindDescriptorSets);
    LOAD_D2(CmdDraw);
    LOAD_D2(CmdDispatch);
    LOAD_D2(CmdPushConstants);
    LOAD_D2(CmdSetViewport);
    LOAD_D2(CmdSetScissor);
    LOAD_D2(CmdPipelineBarrier);
    LOAD_D2(CmdCopyImage);
    LOAD_D2(CmdCopyBufferToImage);
    LOAD_D2(CreateSampler);
    LOAD_D2(DestroySampler);
    LOAD_D2(CreateSemaphore);
    LOAD_D2(DestroySemaphore);
    LOAD_D2(CreateFence);
    LOAD_D2(DestroyFence);
    LOAD_D2(WaitForFences);
    LOAD_D2(ResetFences);
    LOAD_D2(GetFenceStatus);

    vk_.GetAndroidHardwareBufferPropertiesANDROID =
        (PFN_vkGetAndroidHardwareBufferPropertiesANDROID)d("vkGetAndroidHardwareBufferPropertiesANDROID");
}

void VulkanRendererContext::createInstance() {
    RLOG("createInstance: adrenotoolsHandle=%p (custom driver %s)",
        adrenotoolsHandle, adrenotoolsHandle?"ACTIVE":"NOT SET - using stock driver");

    if (adrenotoolsHandle) {
        gipa = (PFN_vkGetInstanceProcAddr)dlsym(adrenotoolsHandle, "vkGetInstanceProcAddr");
    }
    if (!gipa) {
        void* loaderLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
        if (loaderLib)
            gipa = (PFN_vkGetInstanceProcAddr)dlsym(loaderLib, "vkGetInstanceProcAddr");
    }

    vk_.CreateInstance = (PFN_vkCreateInstance)gipa(nullptr, "vkCreateInstance");
    VkApplicationInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;
    ai.pApplicationName="Winlator"; ai.apiVersion=VK_API_VERSION_1_3;
    const char* ext[]={"VK_KHR_surface","VK_KHR_android_surface"};
    VkInstanceCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ci.pApplicationInfo=&ai; ci.enabledExtensionCount=2; ci.ppEnabledExtensionNames=ext;
    if (vk_.CreateInstance(&ci,nullptr,&instance)!=VK_SUCCESS) throw std::runtime_error("instance");

    loadInstanceDispatch();
}

void VulkanRendererContext::createSurface() {
    VkAndroidSurfaceCreateInfoKHR ci{}; ci.sType=VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    ci.window=window;
    if (vk_.CreateAndroidSurfaceKHR(instance,&ci,nullptr,&surface)!=VK_SUCCESS) throw std::runtime_error("surface");
}

void VulkanRendererContext::pickPhysicalDevice() {
    uint32_t n=0; vk_.EnumeratePhysicalDevices(instance,&n,nullptr);
    std::vector<VkPhysicalDevice> devs(n); vk_.EnumeratePhysicalDevices(instance,&n,devs.data());
    physicalDevice = VK_NULL_HANDLE;
    graphicsQueueFamilyIndex = 0;
    for (auto d : devs) {
        uint32_t qCount = 0;
        vk_.GetPhysicalDeviceQueueFamilyProperties(d, &qCount, nullptr);
        std::vector<VkQueueFamilyProperties> qProps(qCount);
        vk_.GetPhysicalDeviceQueueFamilyProperties(d, &qCount, qProps.data());
        for (uint32_t i = 0; i < qCount; i++) {
            VkBool32 present = VK_FALSE;
            vk_.GetPhysicalDeviceSurfaceSupportKHR(d, i, surface, &present);
            if ((qProps[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) {
                physicalDevice = d;
                graphicsQueueFamilyIndex = i;
                return;
            }
        }
    }
    if (n > 0) physicalDevice = devs[0];
}

void VulkanRendererContext::createLogicalDevice() {
    float p=1.f;
    VkDeviceQueueCreateInfo qi{}; qi.sType=VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qi.queueFamilyIndex=graphicsQueueFamilyIndex; qi.queueCount=1; qi.pQueuePriorities=&p;

    PFN_vkEnumerateDeviceExtensionProperties enumDevExts =
        (PFN_vkEnumerateDeviceExtensionProperties)gipa(instance, "vkEnumerateDeviceExtensionProperties");
    { uint32_t n=0; if(enumDevExts) enumDevExts(physicalDevice,nullptr,&n,nullptr);
      std::vector<VkExtensionProperties> av(n);
      if(enumDevExts) enumDevExts(physicalDevice,nullptr,&n,av.data());
      for (auto& e:av) {
          (void)e;
      } }
    std::vector<const char*> extList = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME,
        VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME
    };
    VkDeviceCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    ci.pQueueCreateInfos=&qi; ci.queueCreateInfoCount=1;
    ci.enabledExtensionCount=(uint32_t)extList.size(); ci.ppEnabledExtensionNames=extList.data();
    if (vk_.CreateDevice(physicalDevice,&ci,nullptr,&device)!=VK_SUCCESS) throw std::runtime_error("device");
    vk_.GetDeviceProcAddr = (PFN_vkGetDeviceProcAddr)gipa(instance, "vkGetDeviceProcAddr");
    loadDeviceDispatch();
    vk_.GetDeviceQueue(device,graphicsQueueFamilyIndex,0,&graphicsQueue);

    vk_.GetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

    VkPhysicalDeviceProperties props{};
    vk_.GetPhysicalDeviceProperties(physicalDevice, &props);
    maxAnisotropy = props.limits.maxSamplerAnisotropy;
}

void VulkanRendererContext::createSwapchain() {
    VkSurfaceCapabilitiesKHR caps;
    vk_.GetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice,surface,&caps);

    if (caps.currentExtent.width != 0xFFFFFFFF) {
        swapchainExt = caps.currentExtent;
    } else if (window) {
        int winW = ANativeWindow_getWidth(window);
        int winH = ANativeWindow_getHeight(window);
        swapchainExt = {
            (uint32_t)std::clamp(winW, (int)caps.minImageExtent.width, (int)caps.maxImageExtent.width),
            (uint32_t)std::clamp(winH, (int)caps.minImageExtent.height, (int)caps.maxImageExtent.height)
        };
    } else {
        swapchainExt = {(uint32_t)surfaceWidth, (uint32_t)surfaceHeight};
    }
    uint32_t fmtN=0; vk_.GetPhysicalDeviceSurfaceFormatsKHR(physicalDevice,surface,&fmtN,nullptr);
    std::vector<VkSurfaceFormatKHR> fmts(fmtN); vk_.GetPhysicalDeviceSurfaceFormatsKHR(physicalDevice,surface,&fmtN,fmts.data());
    swapchainFmt = VK_FORMAT_R8G8B8A8_UNORM;
    uint32_t imgCount=caps.minImageCount+1;
    if (caps.maxImageCount>0&&imgCount>caps.maxImageCount) imgCount=caps.maxImageCount;

    uint32_t pmCount=0;
    vk_.GetPhysicalDeviceSurfacePresentModesKHR(physicalDevice,surface,&pmCount,nullptr);
    availablePresentModes.resize(pmCount);
    vk_.GetPhysicalDeviceSurfacePresentModesKHR(physicalDevice,surface,&pmCount,availablePresentModes.data());
    VkPresentModeKHR presentMode=VK_PRESENT_MODE_FIFO_KHR;
    for (auto pm:availablePresentModes) if(pm==requestedPresentMode){presentMode=pm;break;}
    if(verboseLog){
        std::string pmList;
        for(auto pm:availablePresentModes) pmList+=std::to_string((int)pm)+" ";
        RLOG("createSwapchain: %dx%d fmt=%d supportedPresentModes=[%s] chosen=%d req=%d",
            swapchainExt.width,swapchainExt.height,(int)swapchainFmt,pmList.c_str(),(int)presentMode,(int)requestedPresentMode);
    }

    VkSurfaceTransformFlagBitsKHR pre=
        (caps.supportedTransforms&VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)?
        VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR:caps.currentTransform;

    VkCompositeAlphaFlagBitsKHR compositeAlpha=
        (caps.supportedCompositeAlpha&VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)?
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR:VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;

    VkSwapchainKHR oldSwapchain=swapchain;
    VkSwapchainCreateInfoKHR ci{}; ci.sType=VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    ci.surface=surface; ci.minImageCount=imgCount; ci.imageFormat=swapchainFmt;
    ci.imageColorSpace=VK_COLOR_SPACE_SRGB_NONLINEAR_KHR; ci.imageExtent=swapchainExt;
    ci.imageArrayLayers=1; ci.imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    ci.imageSharingMode=VK_SHARING_MODE_EXCLUSIVE; ci.preTransform=pre;
    ci.compositeAlpha=compositeAlpha; ci.presentMode=presentMode; ci.clipped=VK_TRUE;
    ci.oldSwapchain=oldSwapchain;
    if (vk_.CreateSwapchainKHR(device,&ci,nullptr,&swapchain)!=VK_SUCCESS) throw std::runtime_error("swapchain");
    RLOG("swapchain created: %dx%d format=%d presentMode=%d compositeAlpha=%d imgCount=%u",
        swapchainExt.width,swapchainExt.height,(int)swapchainFmt,(int)presentMode,(int)compositeAlpha,imgCount);
    if (oldSwapchain!=VK_NULL_HANDLE) vk_.DestroySwapchainKHR(device,oldSwapchain,nullptr);
    vk_.GetSwapchainImagesKHR(device,swapchain,&imgCount,nullptr);
    swapchainImages.resize(imgCount); vk_.GetSwapchainImagesKHR(device,swapchain,&imgCount,swapchainImages.data());
    swapchainViews.resize(imgCount);
    for (size_t i=0;i<imgCount;i++) {
        VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        vi.image=swapchainImages[i]; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=swapchainFmt;
        vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        VkComponentMapping mapping{};
        mapping.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        vi.components = mapping;
        if (vk_.CreateImageView(device,&vi,nullptr,&swapchainViews[i])!=VK_SUCCESS) throw std::runtime_error("imgview");
    }
}

void VulkanRendererContext::createRenderPass() {
    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED; att.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;
    VkSubpassDependency dep{}; dep.srcSubpass=VK_SUBPASS_EXTERNAL; dep.dstSubpass=0;
    dep.srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT; dep.srcAccessMask=0;
    dep.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=1; ci.pDependencies=&dep;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&renderPass)!=VK_SUCCESS) throw std::runtime_error("renderpass");
}

void VulkanRendererContext::createDSLayout() {
    VkDescriptorSetLayoutBinding b{}; b.binding=0; b.descriptorCount=1;
    b.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; b.stageFlags=VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    ci.bindingCount=1; ci.pBindings=&b;
    if (vk_.CreateDescriptorSetLayout(device,&ci,nullptr,&dsLayout)!=VK_SUCCESS) throw std::runtime_error("dslayout");
}

VkShaderModule VulkanRendererContext::makeShader(const uint32_t* code, size_t sz) {
    VkShaderModuleCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    ci.codeSize=sz; ci.pCode=code; VkShaderModule m;
    if (vk_.CreateShaderModule(device,&ci,nullptr,&m)!=VK_SUCCESS) throw std::runtime_error("shader");
    return m;
}

void VulkanRendererContext::createPipeline(bool blend, VkPipeline& out) {
    if (pipeLayout==VK_NULL_HANDLE) {
        VkPushConstantRange pc{}; pc.stageFlags=VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT;
        pc.size=sizeof(WindowPushConstantsSGSR);
        VkPipelineLayoutCreateInfo li{}; li.sType=VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        li.setLayoutCount=1; li.pSetLayouts=&dsLayout; li.pushConstantRangeCount=1; li.pPushConstantRanges=&pc;
        if (vk_.CreatePipelineLayout(device,&li,nullptr,&pipeLayout)!=VK_SUCCESS) throw std::runtime_error("pipelayout");
    }
    auto vert=makeShader(window_vert_code,sizeof(window_vert_code));
    auto frag=makeShader(window_frag_code,sizeof(window_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=blend?VK_TRUE:VK_FALSE;
    if (blend){ba.srcColorBlendFactor=VK_BLEND_FACTOR_SRC_ALPHA;ba.dstColorBlendFactor=VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;ba.colorBlendOp=VK_BLEND_OP_ADD;ba.srcAlphaBlendFactor=VK_BLEND_FACTOR_ONE;ba.dstAlphaBlendFactor=VK_BLEND_FACTOR_ZERO;ba.alphaBlendOp=VK_BLEND_OP_ADD;}
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&out)!=VK_SUCCESS) throw std::runtime_error("pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
}

void VulkanRendererContext::createCursorPipeline() {  }

void VulkanRendererContext::createSgsrPipeline() {
    if (sgsrPipeline != VK_NULL_HANDLE) return;
    auto vert=makeShader(window_vert_code,sizeof(window_vert_code));
    auto frag=makeShader(window_sgsr_frag_code,sizeof(window_sgsr_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=VK_FALSE;
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&sgsrPipeline)!=VK_SUCCESS) throw std::runtime_error("sgsr_pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
    RLOG("createSgsrPipeline: done");
}

void VulkanRendererContext::createNisPipeline() {
    if (nisPipeline != VK_NULL_HANDLE) return;
    auto vert=makeShader(window_vert_code,sizeof(window_vert_code));
    auto frag=makeShader(window_nis_frag_code,sizeof(window_nis_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=VK_FALSE;
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&nisPipeline)!=VK_SUCCESS) throw std::runtime_error("nis_pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
    RLOG("createNisPipeline: done");
}

void VulkanRendererContext::createLegacyUpscalePipeline() {
    if (legacyUpscalePipeline != VK_NULL_HANDLE) return;
    auto vert = makeShader(window_vert_code, sizeof(window_vert_code));
    auto frag = makeShader(window_legacy_upscale_frag_code, sizeof(window_legacy_upscale_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=VK_FALSE;
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&legacyUpscalePipeline)!=VK_SUCCESS) throw std::runtime_error("legacy_upscale_pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
    RLOG("createLegacyUpscalePipeline: done");
}

void VulkanRendererContext::createStretchPipeline() {
    if (stretchPipeline != VK_NULL_HANDLE) return;
    auto vert=makeShader(window_vert_code,sizeof(window_vert_code));
    auto frag=makeShader(window_stretch_frag_code,sizeof(window_stretch_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=VK_FALSE;
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&stretchPipeline)!=VK_SUCCESS) throw std::runtime_error("stretch_pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
    RLOG("createStretchPipeline: done");
}

void VulkanRendererContext::createPostFXPipeline() {
    if (postfxPipeline != VK_NULL_HANDLE) return;
    auto vert = makeShader(window_vert_code,        sizeof(window_vert_code));
    auto frag = makeShader(window_postfx_frag_code, sizeof(window_postfx_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT;   stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo   vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=VK_FALSE;
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&postfxPipeline)!=VK_SUCCESS) throw std::runtime_error("postfx_pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
    RLOG("createPostFXPipeline: done");
}

void VulkanRendererContext::createFrameGenPipelines() {
    if (frameGenMotionPipeline != VK_NULL_HANDLE && frameGenInterpPipeline != VK_NULL_HANDLE) return;

    VkAttachmentDescription historyAttachment{};
    historyAttachment.format = swapchainFmt;
    historyAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    historyAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    historyAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    historyAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    historyAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    historyAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    historyAttachment.finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    VkAttachmentReference historyRef{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription historySubpass{};
    historySubpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    historySubpass.colorAttachmentCount = 1;
    historySubpass.pColorAttachments = &historyRef;
    VkSubpassDependency historyDependencies[2]{};
    historyDependencies[0].srcSubpass = VK_SUBPASS_EXTERNAL;
    historyDependencies[0].dstSubpass = 0;
    historyDependencies[0].srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    historyDependencies[0].dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    historyDependencies[0].srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
    historyDependencies[0].dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    historyDependencies[1].srcSubpass = 0;
    historyDependencies[1].dstSubpass = VK_SUBPASS_EXTERNAL;
    historyDependencies[1].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    historyDependencies[1].dstStageMask = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    historyDependencies[1].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    historyDependencies[1].dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    VkRenderPassCreateInfo historyPassInfo{};
    historyPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    historyPassInfo.attachmentCount = 1;
    historyPassInfo.pAttachments = &historyAttachment;
    historyPassInfo.subpassCount = 1;
    historyPassInfo.pSubpasses = &historySubpass;
    historyPassInfo.dependencyCount = 2;
    historyPassInfo.pDependencies = historyDependencies;
    if (vk_.CreateRenderPass(device, &historyPassInfo, nullptr, &frameGenHistoryPass) != VK_SUCCESS)
        throw std::runtime_error("framegen history renderpass");

    VkDescriptorSetLayoutBinding motionBindings[3]{};
    for (uint32_t i = 0; i < 2; i++) {
        motionBindings[i].binding = i;
        motionBindings[i].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        motionBindings[i].descriptorCount = 1;
        motionBindings[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    motionBindings[2].binding = 2;
    motionBindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    motionBindings[2].descriptorCount = 1;
    motionBindings[2].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    VkDescriptorSetLayoutCreateInfo motionLayoutInfo{};
    motionLayoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    motionLayoutInfo.bindingCount = 3;
    motionLayoutInfo.pBindings = motionBindings;
    if (vk_.CreateDescriptorSetLayout(device, &motionLayoutInfo, nullptr, &frameGenMotionLayout) != VK_SUCCESS)
        throw std::runtime_error("framegen motion layout");

    VkDescriptorSetLayoutBinding interpBindings[3]{};
    for (uint32_t i = 0; i < 3; i++) {
        interpBindings[i].binding = i;
        interpBindings[i].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        interpBindings[i].descriptorCount = 1;
        interpBindings[i].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    }
    VkDescriptorSetLayoutCreateInfo interpLayoutInfo{};
    interpLayoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    interpLayoutInfo.bindingCount = 3;
    interpLayoutInfo.pBindings = interpBindings;
    if (vk_.CreateDescriptorSetLayout(device, &interpLayoutInfo, nullptr, &frameGenInterpLayout) != VK_SUCCESS)
        throw std::runtime_error("framegen interp layout");

    VkPushConstantRange motionPush{VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(FrameGenMotionPush)};
    VkPipelineLayoutCreateInfo motionPipeLayoutInfo{};
    motionPipeLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    motionPipeLayoutInfo.setLayoutCount = 1;
    motionPipeLayoutInfo.pSetLayouts = &frameGenMotionLayout;
    motionPipeLayoutInfo.pushConstantRangeCount = 1;
    motionPipeLayoutInfo.pPushConstantRanges = &motionPush;
    if (vk_.CreatePipelineLayout(device, &motionPipeLayoutInfo, nullptr, &frameGenMotionPipeLayout) != VK_SUCCESS)
        throw std::runtime_error("framegen motion pipeline layout");

    VkPushConstantRange interpPush{VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(FrameGenInterpPush)};
    VkPipelineLayoutCreateInfo interpPipeLayoutInfo{};
    interpPipeLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    interpPipeLayoutInfo.setLayoutCount = 1;
    interpPipeLayoutInfo.pSetLayouts = &frameGenInterpLayout;
    interpPipeLayoutInfo.pushConstantRangeCount = 1;
    interpPipeLayoutInfo.pPushConstantRanges = &interpPush;
    if (vk_.CreatePipelineLayout(device, &interpPipeLayoutInfo, nullptr, &frameGenInterpPipeLayout) != VK_SUCCESS)
        throw std::runtime_error("framegen interp pipeline layout");

    VkShaderModule motionShader = makeShader(framegen_motion_comp_code, sizeof(framegen_motion_comp_code));
    VkPipelineShaderStageCreateInfo motionStage{};
    motionStage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    motionStage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    motionStage.module = motionShader;
    motionStage.pName = "main";
    VkComputePipelineCreateInfo motionPipelineInfo{};
    motionPipelineInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    motionPipelineInfo.stage = motionStage;
    motionPipelineInfo.layout = frameGenMotionPipeLayout;
    if (vk_.CreateComputePipelines(device, VK_NULL_HANDLE, 1, &motionPipelineInfo, nullptr,
                                   &frameGenMotionPipeline) != VK_SUCCESS) {
        vk_.DestroyShaderModule(device, motionShader, nullptr);
        throw std::runtime_error("framegen motion pipeline");
    }
    vk_.DestroyShaderModule(device, motionShader, nullptr);

    VkShaderModule vertexShader = makeShader(framegen_vert_code, sizeof(framegen_vert_code));
    VkShaderModule interpShader = makeShader(framegen_interpolate_frag_code, sizeof(framegen_interpolate_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vertexShader;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = interpShader;
    stages[1].pName = "main";
    VkPipelineVertexInputStateCreateInfo vertexInput{};
    vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo assembly{};
    assembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamic{};
    dynamic.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
    dynamic.dynamicStateCount = 2;
    dynamic.pDynamicStates = dynamicStates;
    VkPipelineViewportStateCreateInfo viewport{};
    viewport.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewport.viewportCount = 1;
    viewport.scissorCount = 1;
    VkPipelineRasterizationStateCreateInfo raster{};
    raster.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    raster.polygonMode = VK_POLYGON_MODE_FILL;
    raster.lineWidth = 1.0f;
    raster.cullMode = VK_CULL_MODE_NONE;
    raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo multisample{};
    multisample.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState blendAttachment{};
    blendAttachment.colorWriteMask = 0xf;
    VkPipelineColorBlendStateCreateInfo blend{};
    blend.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    blend.attachmentCount = 1;
    blend.pAttachments = &blendAttachment;
    VkGraphicsPipelineCreateInfo interpPipelineInfo{};
    interpPipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    interpPipelineInfo.stageCount = 2;
    interpPipelineInfo.pStages = stages;
    interpPipelineInfo.pVertexInputState = &vertexInput;
    interpPipelineInfo.pInputAssemblyState = &assembly;
    interpPipelineInfo.pViewportState = &viewport;
    interpPipelineInfo.pRasterizationState = &raster;
    interpPipelineInfo.pMultisampleState = &multisample;
    interpPipelineInfo.pColorBlendState = &blend;
    interpPipelineInfo.pDynamicState = &dynamic;
    interpPipelineInfo.layout = frameGenInterpPipeLayout;
    interpPipelineInfo.renderPass = renderPass;
    if (vk_.CreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &interpPipelineInfo, nullptr,
                                    &frameGenInterpPipeline) != VK_SUCCESS) {
        vk_.DestroyShaderModule(device, interpShader, nullptr);
        vk_.DestroyShaderModule(device, vertexShader, nullptr);
        throw std::runtime_error("framegen interp pipeline");
    }
    vk_.DestroyShaderModule(device, interpShader, nullptr);
    vk_.DestroyShaderModule(device, vertexShader, nullptr);
    RLOG("Native Framegen pipelines created");
}

bool VulkanRendererContext::createFrameGenImage(FrameGenImage& out, uint32_t width, uint32_t height,
                                                VkFormat format, VkImageUsageFlags usage,
                                                bool needsFramebuffer) {
    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent = {width, height, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = format;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = usage;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateImage(device, &imageInfo, nullptr, &out.image) != VK_SUCCESS) return false;
    VkMemoryRequirements requirements{};
    vk_.GetImageMemoryRequirements(device, out.image, &requirements);
    VkMemoryAllocateInfo allocation{};
    allocation.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocation.allocationSize = requirements.size;
    allocation.memoryTypeIndex = findMemType(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (vk_.AllocateMemory(device, &allocation, nullptr, &out.memory) != VK_SUCCESS) return false;
    if (vk_.BindImageMemory(device, out.image, out.memory, 0) != VK_SUCCESS) return false;
    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = out.image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = format;
    viewInfo.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (vk_.CreateImageView(device, &viewInfo, nullptr, &out.view) != VK_SUCCESS) return false;
    if (needsFramebuffer) {
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = frameGenHistoryPass;
        framebufferInfo.attachmentCount = 1;
        framebufferInfo.pAttachments = &out.view;
        framebufferInfo.width = width;
        framebufferInfo.height = height;
        framebufferInfo.layers = 1;
        if (vk_.CreateFramebuffer(device, &framebufferInfo, nullptr, &out.framebuffer) != VK_SUCCESS) return false;
    }
    return true;
}

bool VulkanRendererContext::createFrameGenResources() {
    if (frameGenResourcesBuilt) return true;
    if (swapchainExt.width == 0 || swapchainExt.height == 0) return false;
    destroyFrameGenResources();
    // Native frame generation is optional. Avoid compiling its compute and
    // interpolation pipelines during renderer startup when the feature is off.
    createFrameGenPipelines();

    VkSamplerCreateInfo samplerInfo{};
    samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
    samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    if (vk_.CreateSampler(device, &samplerInfo, nullptr, &frameGenSampler) != VK_SUCCESS) return false;

    if (!createFrameGenImage(frameGenHistory[0], swapchainExt.width, swapchainExt.height, swapchainFmt,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, true) ||
        !createFrameGenImage(frameGenHistory[1], swapchainExt.width, swapchainExt.height, swapchainFmt,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, true) ||
        !createFrameGenImage(frameGenMotion, std::max(1u, swapchainExt.width / 2),
            std::max(1u, swapchainExt.height / 2), VK_FORMAT_R16G16B16A16_SFLOAT,
            VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, false)) {
        destroyFrameGenResources();
        return false;
    }

    VkCommandBufferAllocateInfo commandInfo{};
    commandInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    commandInfo.commandPool = cmdPool;
    commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    commandInfo.commandBufferCount = 1;
    if (vk_.AllocateCommandBuffers(device, &commandInfo, &frameGenStageCmd) != VK_SUCCESS) {
        destroyFrameGenResources();
        return false;
    }
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    if (vk_.CreateFence(device, &fenceInfo, nullptr, &frameGenStageFence) != VK_SUCCESS) {
        destroyFrameGenResources();
        return false;
    }

    VkCommandBuffer transitionCmd = beginOneTime();
    transition(transitionCmd, frameGenMotion.image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
               0, VK_ACCESS_SHADER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
               VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
    endOneTime(transitionCmd);

    for (uint32_t parity = 0; parity < 2; parity++) {
        VkDescriptorSetLayout layouts[] = {frameGenMotionLayout, frameGenInterpLayout};
        VkDescriptorSet sets[2]{};
        VkDescriptorSetAllocateInfo allocationInfo{};
        allocationInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        allocationInfo.descriptorPool = winTexPool;
        allocationInfo.descriptorSetCount = 2;
        allocationInfo.pSetLayouts = layouts;
        if (vk_.AllocateDescriptorSets(device, &allocationInfo, sets) != VK_SUCCESS) {
            destroyFrameGenResources();
            return false;
        }
        frameGenMotionSets[parity] = sets[0];
        frameGenInterpSets[parity] = sets[1];
        VkDescriptorImageInfo prevInfo{frameGenSampler, frameGenHistory[parity ^ 1u].view,
                                       VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkDescriptorImageInfo currInfo{frameGenSampler, frameGenHistory[parity].view,
                                       VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkDescriptorImageInfo motionStorage{VK_NULL_HANDLE, frameGenMotion.view, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorImageInfo motionSample{frameGenSampler, frameGenMotion.view, VK_IMAGE_LAYOUT_GENERAL};
        VkWriteDescriptorSet writes[6]{};
        VkDescriptorImageInfo* motionInfos[] = {&prevInfo, &currInfo, &motionStorage};
        for (uint32_t i = 0; i < 3; i++) {
            writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            writes[i].dstSet = frameGenMotionSets[parity];
            writes[i].dstBinding = i;
            writes[i].descriptorCount = 1;
            writes[i].descriptorType = i == 2 ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                                              : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            writes[i].pImageInfo = motionInfos[i];
        }
        VkDescriptorImageInfo* interpInfos[] = {&prevInfo, &currInfo, &motionSample};
        for (uint32_t i = 0; i < 3; i++) {
            writes[3 + i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            writes[3 + i].dstSet = frameGenInterpSets[parity];
            writes[3 + i].dstBinding = i;
            writes[3 + i].descriptorCount = 1;
            writes[3 + i].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            writes[3 + i].pImageInfo = interpInfos[i];
        }
        vk_.UpdateDescriptorSets(device, 6, writes, 0, nullptr);
    }

    frameGenHistoryCurrent = 0;
    frameGenHistoryCount = 0;
    frameGenMotionValid = false;
    frameGenResourcesBuilt = true;
    RLOG("Native Framegen resources created: %ux%u", swapchainExt.width, swapchainExt.height);
    return true;
}

void VulkanRendererContext::destroyFrameGenResources() {
    frameGenResourcesBuilt = false;
    frameGenHistoryCount = 0;
    frameGenMotionValid = false;
    if (device == VK_NULL_HANDLE) return;
    if (frameGenStageFence != VK_NULL_HANDLE) {
        vk_.DestroyFence(device, frameGenStageFence, nullptr);
        frameGenStageFence = VK_NULL_HANDLE;
    }
    if (frameGenStageCmd != VK_NULL_HANDLE && cmdPool != VK_NULL_HANDLE) {
        vk_.FreeCommandBuffers(device, cmdPool, 1, &frameGenStageCmd);
        frameGenStageCmd = VK_NULL_HANDLE;
    }
    for (uint32_t i = 0; i < 2; i++) {
        if (frameGenMotionSets[i] != VK_NULL_HANDLE && winTexPool != VK_NULL_HANDLE)
            vk_.FreeDescriptorSets(device, winTexPool, 1, &frameGenMotionSets[i]);
        if (frameGenInterpSets[i] != VK_NULL_HANDLE && winTexPool != VK_NULL_HANDLE)
            vk_.FreeDescriptorSets(device, winTexPool, 1, &frameGenInterpSets[i]);
        frameGenMotionSets[i] = VK_NULL_HANDLE;
        frameGenInterpSets[i] = VK_NULL_HANDLE;
    }
    auto destroyImage = [&](FrameGenImage& image) {
        if (image.framebuffer != VK_NULL_HANDLE) vk_.DestroyFramebuffer(device, image.framebuffer, nullptr);
        if (image.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, image.view, nullptr);
        if (image.image != VK_NULL_HANDLE) vk_.DestroyImage(device, image.image, nullptr);
        if (image.memory != VK_NULL_HANDLE) vk_.FreeMemory(device, image.memory, nullptr);
        image = {};
    };
    destroyImage(frameGenHistory[0]);
    destroyImage(frameGenHistory[1]);
    destroyImage(frameGenMotion);
    if (frameGenSampler != VK_NULL_HANDLE) {
        vk_.DestroySampler(device, frameGenSampler, nullptr);
        frameGenSampler = VK_NULL_HANDLE;
    }
}

void VulkanRendererContext::setPostFXMode(int mode) {
    RLOG("setPostFXMode: %d -> %d", postFXMode, mode);
    if (postFXMode == mode) return;
    postFXMode = mode;
    if (mode > 0) {

        if (filterMode != 2 && filterMode != 3 && filterMode != 4 && filterMode != 5
            && postfxPipeline == VK_NULL_HANDLE)
            createPostFXPipeline();
    } else {

        if (postfxPipeline != VK_NULL_HANDLE) {
            vk_.DeviceWaitIdle(device);
            vk_.DestroyPipeline(device, postfxPipeline, nullptr);
            postfxPipeline = VK_NULL_HANDLE;
            RLOG("setPostFXMode: postfxPipeline destruído");
        }
    }
    invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setSharpness(float s) {
    float clamped = std::clamp(s, 0.0f, 1.0f);
    if (sharpness == clamped) return;
    sharpness = clamped;
    invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::createFramebuffers() {
    swapchainFBs.resize(swapchainViews.size());
    for (size_t i=0;i<swapchainViews.size();i++) {
        VkImageView att[]={swapchainViews[i]};
        VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fi.renderPass=renderPass; fi.attachmentCount=1; fi.pAttachments=att;
        fi.width=swapchainExt.width; fi.height=swapchainExt.height; fi.layers=1;
        if (vk_.CreateFramebuffer(device,&fi,nullptr,&swapchainFBs[i])!=VK_SUCCESS) throw std::runtime_error("fb");
    }
}

void VulkanRendererContext::createCmdPool() {
    VkCommandPoolCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    ci.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT; ci.queueFamilyIndex=graphicsQueueFamilyIndex;
    if (vk_.CreateCommandPool(device,&ci,nullptr,&cmdPool)!=VK_SUCCESS) throw std::runtime_error("cmdpool");
}

void VulkanRendererContext::createSampler() {
    VkFilter filter = (filterMode == 1) ? VK_FILTER_NEAREST : VK_FILTER_LINEAR;
    RLOG("createSampler: filter=%s (filterMode=%d)",
        filterMode==1?"NEAREST":"LINEAR", filterMode);
    VkSamplerCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    ci.magFilter=filter; ci.minFilter=filter;
    ci.addressModeU=ci.addressModeV=ci.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
    ci.minLod=0.f; ci.maxLod=0.f;
    if (vk_.CreateSampler(device,&ci,nullptr,&sampler)!=VK_SUCCESS) throw std::runtime_error("sampler");
}

void VulkanRendererContext::createWinTexPool() {
    VkDescriptorPoolSize ps[2] = {
        {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 160},
        {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 8}
    };
    VkDescriptorPoolCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    ci.flags=VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
    ci.poolSizeCount=2; ci.pPoolSizes=ps; ci.maxSets=160;
    if (vk_.CreateDescriptorPool(device,&ci,nullptr,&winTexPool)!=VK_SUCCESS) throw std::runtime_error("wintexpool");
}

void VulkanRendererContext::createCursorDS() {
    VkDescriptorSetAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    ai.descriptorPool=winTexPool; ai.descriptorSetCount=1; ai.pSetLayouts=&dsLayout;
    vk_.AllocateDescriptorSets(device,&ai,&cursorDS);
}

void VulkanRendererContext::createCmdBufs() {
    cmdBufs.resize(MAX_FRAMES_IN_FLIGHT);
    VkCommandBufferAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.commandPool=cmdPool; ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandBufferCount=MAX_FRAMES_IN_FLIGHT;
    if (vk_.AllocateCommandBuffers(device,&ai,cmdBufs.data())!=VK_SUCCESS) throw std::runtime_error("cmdbuf");
}

void VulkanRendererContext::createSyncObjects() {
    imgAvailSems.resize(MAX_FRAMES_IN_FLIGHT); renderDoneSems.resize(MAX_FRAMES_IN_FLIGHT); inFlightFences.resize(MAX_FRAMES_IN_FLIGHT);
    VkSemaphoreCreateInfo si{}; si.sType=VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
    for (uint32_t i=0;i<MAX_FRAMES_IN_FLIGHT;i++) {
        if (vk_.CreateSemaphore(device,&si,nullptr,&imgAvailSems[i])!=VK_SUCCESS||
            vk_.CreateSemaphore(device,&si,nullptr,&renderDoneSems[i])!=VK_SUCCESS||
            vk_.CreateFence(device,&fi,nullptr,&inFlightFences[i])!=VK_SUCCESS) throw std::runtime_error("sync");
    }
}

void VulkanRendererContext::cleanupSwapchain() {
    destroyFrameGenResources();
    for (auto fb:swapchainFBs) vk_.DestroyFramebuffer(device,fb,nullptr); swapchainFBs.clear();
    for (auto iv:swapchainViews) vk_.DestroyImageView(device,iv,nullptr); swapchainViews.clear();
    if (!cmdBufs.empty()){vk_.FreeCommandBuffers(device,cmdPool,(uint32_t)cmdBufs.size(),cmdBufs.data());cmdBufs.clear();}
    if (swapchain!=VK_NULL_HANDLE) { vk_.DestroySwapchainKHR(device,swapchain,nullptr); swapchain=VK_NULL_HANDLE; }
}

uint32_t VulkanRendererContext::findMemType(uint32_t filter, VkMemoryPropertyFlags props) {
    for (uint32_t i=0;i<memProperties.memoryTypeCount;i++)
        if ((filter&(1u<<i))&&(memProperties.memoryTypes[i].propertyFlags&props)==props) return i;
    throw std::runtime_error("memtype");
}

void VulkanRendererContext::createBuffer(VkDeviceSize sz, VkBufferUsageFlags usage,
    VkMemoryPropertyFlags props, VkBuffer& buf, VkDeviceMemory& mem)
{
    VkBufferCreateInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO; bi.size=sz; bi.usage=usage; bi.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateBuffer(device,&bi,nullptr,&buf)!=VK_SUCCESS) throw std::runtime_error("buffer");
    VkMemoryRequirements req; vk_.GetBufferMemoryRequirements(device,buf,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,props);
    if (vk_.AllocateMemory(device,&ai,nullptr,&mem)!=VK_SUCCESS) throw std::runtime_error("bufmem");
    vk_.BindBufferMemory(device,buf,mem,0);
}

VkCommandBuffer VulkanRendererContext::beginOneTime() {
    VkCommandBufferAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandPool=cmdPool; ai.commandBufferCount=1;
    VkCommandBuffer cb; vk_.AllocateCommandBuffers(device,&ai,&cb);
    VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO; bi.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vk_.BeginCommandBuffer(cb,&bi); return cb;
}

void VulkanRendererContext::endOneTime(VkCommandBuffer cb) {
    vk_.EndCommandBuffer(cb);
    VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO; si.commandBufferCount=1; si.pCommandBuffers=&cb;
    VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; VkFence fence;
    vk_.CreateFence(device,&fi,nullptr,&fence);
    vk_.QueueSubmit(graphicsQueue,1,&si,fence); vk_.WaitForFences(device,1,&fence,VK_TRUE,UINT64_MAX);
    vk_.DestroyFence(device,fence,nullptr); vk_.FreeCommandBuffers(device,cmdPool,1,&cb);
}

void VulkanRendererContext::transition(VkCommandBuffer cb, VkImage img,
    VkImageLayout ol, VkImageLayout nl, VkAccessFlags sa, VkAccessFlags da,
    VkPipelineStageFlags ss, VkPipelineStageFlags ds)
{
    VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    b.oldLayout=ol; b.newLayout=nl; b.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    b.image=img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}; b.srcAccessMask=sa; b.dstAccessMask=da;
    vk_.CmdPipelineBarrier(cb,ss,ds,0,0,nullptr,0,nullptr,1,&b);
}

bool VulkanRendererContext::createWinTexResources(WinTex& wt, int w, int h) {

    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=VK_FORMAT_B8G8R8A8_UNORM;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateImage(device,&ii,nullptr,&wt.img)!=VK_SUCCESS) return false;
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,wt.img,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (vk_.AllocateMemory(device,&ai,nullptr,&wt.mem)!=VK_SUCCESS){vk_.DestroyImage(device,wt.img,nullptr);wt.img=VK_NULL_HANDLE;return false;}
    vk_.BindImageMemory(device,wt.img,wt.mem,0);
    VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vi.image=wt.img; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=VK_FORMAT_B8G8R8A8_UNORM; vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vi.components={swapRB?VK_COMPONENT_SWIZZLE_B:VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,swapRB?VK_COMPONENT_SWIZZLE_R:VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    if (vk_.CreateImageView(device,&vi,nullptr,&wt.view)!=VK_SUCCESS){destroyWinTex(wt);return false;}
    VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO; dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    if (vk_.AllocateDescriptorSets(device,&dsai,&wt.ds)!=VK_SUCCESS){destroyWinTex(wt);return false;}
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=wt.view; dii.sampler=sampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=wt.ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    VkDeviceSize stgSz=(VkDeviceSize)w*h*4;
    createBuffer(stgSz,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,wt.stg,wt.stgMem);
    vk_.MapMemory(device,wt.stgMem,0,stgSz,0,&wt.mapped);
    wt.cap=stgSz; wt.w=w; wt.h=h; wt.needsTransition=true;
    return true;
}

bool VulkanRendererContext::importAHBToWinTex(WinTex& wt, AHardwareBuffer* ahb) {
    if (!vk_.GetAndroidHardwareBufferPropertiesANDROID)
        return false;

    VkAndroidHardwareBufferFormatPropertiesANDROID fmtP{};
    fmtP.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID;
    VkAndroidHardwareBufferPropertiesANDROID props{};
    props.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    props.pNext=&fmtP;
    if (vk_.GetAndroidHardwareBufferPropertiesANDROID(device,ahb,&props)!=VK_SUCCESS)
        return false;

    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(ahb,&desc);

    VkExternalFormatANDROID ef{};
    ef.sType=VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID;
    ef.externalFormat=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;

    VkExternalMemoryImageCreateInfo emi{};
    emi.sType=VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    emi.handleTypes=VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
    ef.pNext=const_cast<void*>(emi.pNext);
    emi.pNext=&ef;

    VkImageCreateInfo ii{};
    ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ii.pNext=&emi; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.format=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;
    ii.extent={desc.width,desc.height,1};
    ii.mipLevels=1; ii.arrayLayers=1; ii.samples=VK_SAMPLE_COUNT_1_BIT;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.usage=VK_IMAGE_USAGE_SAMPLED_BIT;
    ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    if (vk_.CreateImage(device,&ii,nullptr,&wt.img)!=VK_SUCCESS)
        return false;

    VkImportAndroidHardwareBufferInfoANDROID imp{};
    imp.sType=VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    imp.buffer=ahb;

    VkMemoryDedicatedAllocateInfo ded{};
    ded.sType=VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    ded.pNext=&imp; ded.image=wt.img;

    VkMemoryAllocateInfo mai{};
    mai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.pNext=&ded; mai.allocationSize=props.allocationSize;
    mai.memoryTypeIndex=findMemType(props.memoryTypeBits,0);
    if (vk_.AllocateMemory(device,&mai,nullptr,&wt.mem)!=VK_SUCCESS){
        vk_.DestroyImage(device,wt.img,nullptr);
        wt.img=VK_NULL_HANDLE;
        return false;
    }
    vk_.BindImageMemory(device,wt.img,wt.mem,0);

    VkExternalFormatANDROID vef{};
    vef.sType=VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID;
    vef.externalFormat=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;

    VkImageViewCreateInfo vi{};
    vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    vi.pNext=&vef; vi.image=wt.img; vi.viewType=VK_IMAGE_VIEW_TYPE_2D;
    vi.format=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;
    vi.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,
                   VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    if (vk_.CreateImageView(device,&vi,nullptr,&wt.view)!=VK_SUCCESS){
        destroyWinTex(wt);
        return false;
    }

    VkDescriptorSetAllocateInfo dsai{};
    dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    VkResult dsRes=vk_.AllocateDescriptorSets(device,&dsai,&wt.ds);
    if (dsRes==VK_ERROR_OUT_OF_POOL_MEMORY){
        RLOG_E("importAHBToWinTex: descriptor pool exhausted for AHB texture");
        destroyWinTex(wt);
        return false;
    }
    if (dsRes!=VK_SUCCESS){ destroyWinTex(wt); return false; }

    VkDescriptorImageInfo dii{};
    dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    dii.imageView=wt.view; dii.sampler=sampler;

    VkWriteDescriptorSet wr{};
    wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    wr.dstSet=wt.ds; wr.dstBinding=0;
    wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);

    wt.needsTransition=true;
    wt.isAHB=true;
    wt.w=(int)desc.width;
    wt.h=(int)desc.height;
    return true;
}

void VulkanRendererContext::destroyWinTex(WinTex& wt) {
    if (wt.isAHB) {

        wt = {};
        return;
    }
    if (wt.img!=VK_NULL_HANDLE || wt.stg!=VK_NULL_HANDLE) {

        WinTex deferred = wt;
        deferred.isAHB = false;
        deleteQueue.push_back(deferred);
    }
    wt={};
}

void VulkanRendererContext::ensureCursorTex(short w, short h) {
    if (cursorImg!=VK_NULL_HANDLE && cursorTexW==w && cursorTexH==h) return;
    cleanupCursorTex();
    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=VK_FORMAT_B8G8R8A8_UNORM;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    vk_.CreateImage(device,&ii,nullptr,&cursorImg);
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,cursorImg,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    vk_.AllocateMemory(device,&ai,nullptr,&cursorMem); vk_.BindImageMemory(device,cursorImg,cursorMem,0);
    VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vi.image=cursorImg; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=VK_FORMAT_B8G8R8A8_UNORM; vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vk_.CreateImageView(device,&vi,nullptr,&cursorView);
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=cursorView; dii.sampler=sampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=cursorDS; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);

    cursorTexW=w; cursorTexH=h;
    cursorImageInitialized=false;
}

void VulkanRendererContext::cleanupCursorTex() {
    if (cursorView!=VK_NULL_HANDLE){vk_.DestroyImageView(device,cursorView,nullptr);cursorView=VK_NULL_HANDLE;}
    if (cursorImg!=VK_NULL_HANDLE){vk_.DestroyImage(device,cursorImg,nullptr);cursorImg=VK_NULL_HANDLE;}
    if (cursorMem!=VK_NULL_HANDLE){vk_.FreeMemory(device,cursorMem,nullptr);cursorMem=VK_NULL_HANDLE;}
    if (cursorStg!=VK_NULL_HANDLE){vk_.DestroyBuffer(device,cursorStg,nullptr);vk_.FreeMemory(device,cursorStgM,nullptr);cursorStg=VK_NULL_HANDLE;cursorStgP=nullptr;cursorStgC=0;}
    cursorTexW=0; cursorTexH=0;
    cursorImageInitialized=false;
}

void VulkanRendererContext::ensureCursorStaging(VkDeviceSize sz) {
    if (cursorStgC>=sz) return;
    if (cursorStg!=VK_NULL_HANDLE){vk_.DestroyBuffer(device,cursorStg,nullptr);vk_.FreeMemory(device,cursorStgM,nullptr);}
    createBuffer(sz,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,cursorStg,cursorStgM);
    vk_.MapMemory(device,cursorStgM,0,sz,0,&cursorStgP); cursorStgC=sz;
}

void VulkanRendererContext::recordCmdBuf(VkCommandBuffer cb, VkRenderPass targetRenderPass, VkFramebuffer targetFramebuffer,
    const std::vector<DrawEntry>& draws,
    std::vector<VkImageMemoryBarrier>& ahbTransitions,
    std::vector<VkImageMemoryBarrier>& preUpload,
    std::vector<VkImageMemoryBarrier>& postUpload,
    VkBuffer cursorUpload, bool hasCursorUpload,
    float ox, float oy, float sx, float sy, float cw, float ch,
    short ptrX, short ptrY, short curHotX, short curHotY,
    short curW, short curH, bool curVis,
    VkRect2D scissorRect)
{
    VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    if (vk_.BeginCommandBuffer(cb,&bi)!=VK_SUCCESS) throw std::runtime_error("begin cb");

    ahbTransitions.clear(); preUpload.clear(); postUpload.clear();

    for (auto& d : draws) {
        if (d.img==VK_NULL_HANDLE) continue;
        if (d.isAHB && d.needsTransition) {
            VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            b.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            b.image=d.img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            b.srcAccessMask=0; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            ahbTransitions.push_back(b);
        } else if (!d.isAHB && (d.needsTransition || d.upload!=VK_NULL_HANDLE)) {
            VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            b.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; b.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            b.image=d.img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            b.srcAccessMask=0; b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            preUpload.push_back(b);
            b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            postUpload.push_back(b);
        }
    }

    if (!ahbTransitions.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)ahbTransitions.size(), ahbTransitions.data());
    if (!preUpload.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)preUpload.size(), preUpload.data());

    for (auto& d : draws) {
        if (d.isAHB || d.upload==VK_NULL_HANDLE || d.img==VK_NULL_HANDLE) continue;
        VkBufferImageCopy r{}; r.bufferOffset=0; r.bufferRowLength=0; r.bufferImageHeight=0;
        r.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        r.imageExtent={(uint32_t)d.w,(uint32_t)d.h,1};
        vk_.CmdCopyBufferToImage(cb, d.upload, d.img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &r);
    }

    bool cursorDrawn = curVis && cursorImg!=VK_NULL_HANDLE && cursorDS!=VK_NULL_HANDLE;
    bool hasCursorCopy = hasCursorUpload && cursorImg!=VK_NULL_HANDLE && cursorUpload!=VK_NULL_HANDLE;
    if (hasCursorCopy) {
        VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        b.oldLayout=cursorImageInitialized ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED;
        b.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        b.image=cursorImg; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        b.srcAccessMask=cursorImageInitialized ? VK_ACCESS_SHADER_READ_BIT : 0;
        b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
        vk_.CmdPipelineBarrier(cb,
            cursorImageInitialized ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            0, 0, nullptr, 0, nullptr, 1, &b);
        VkBufferImageCopy r{}; r.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        r.imageExtent={(uint32_t)curW,(uint32_t)curH,1};
        vk_.CmdCopyBufferToImage(cb, cursorUpload, cursorImg, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &r);
        b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
        postUpload.push_back(b);
        cursorImageInitialized=true;
    }

    if (!postUpload.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)postUpload.size(), postUpload.data());

    VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rpi.renderPass=targetRenderPass; rpi.framebuffer=targetFramebuffer; rpi.renderArea={{0,0},swapchainExt};
    VkClearValue clr={{{0.f,0.f,0.f,1.f}}}; rpi.clearValueCount=1; rpi.pClearValues=&clr;

    vk_.CmdBeginRenderPass(cb, &rpi, VK_SUBPASS_CONTENTS_INLINE);
    VkViewport vp{0,0,(float)swapchainExt.width,(float)swapchainExt.height,0,1};
    vk_.CmdSetViewport(cb, 0, 1, &vp);

    {
        int32_t  ox2 = std::max(0, scissorRect.offset.x);
        int32_t  oy2 = std::max(0, scissorRect.offset.y);
        uint32_t maxW = (swapchainExt.width  > (uint32_t)ox2) ? swapchainExt.width  - (uint32_t)ox2 : 0u;
        uint32_t maxH = (swapchainExt.height > (uint32_t)oy2) ? swapchainExt.height - (uint32_t)oy2 : 0u;
        VkRect2D sc{{ox2, oy2},
                    {std::min(scissorRect.extent.width,  maxW),
                     std::min(scissorRect.extent.height, maxH)}};
        vk_.CmdSetScissor(cb, 0, 1, &sc);
    }

    bool useSgsr    = (filterMode == 2) && (sgsrPipeline    != VK_NULL_HANDLE);
    bool useNis     = (filterMode == 3) && (nisPipeline     != VK_NULL_HANDLE);
    bool useLegacyUpscale = (filterMode == 4 || filterMode == 5) && (legacyUpscalePipeline != VK_NULL_HANDLE);
    bool usePostFX  = (postFXMode  > 0) && !useNis && !useLegacyUpscale
        && (postfxPipeline  != VK_NULL_HANDLE);
    bool useStretch = (stretchMode == 1) && (stretchPipeline != VK_NULL_HANDLE);

    VkPipeline activePipeline = useSgsr   ? sgsrPipeline
                              : useNis    ? nisPipeline
                              : useLegacyUpscale ? legacyUpscalePipeline
                              : usePostFX ? postfxPipeline
                              : useStretch? stretchPipeline
                              : pipeline;
    vk_.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, activePipeline);
    for (auto& d : draws) {
        if (d.ds==VK_NULL_HANDLE) continue;
        vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &d.ds, 0, nullptr);
        if (useSgsr) {
            WindowPushConstantsSGSR pc{};

            if (useStretch) {
                pc.ndcX0 = -1.f;
                pc.ndcX1 =  1.f;
            } else {
                pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
                pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            }
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.useTexAlpha = 0;
            float sw = (float)std::max(d.w, 1); float sh = (float)std::max(d.h, 1);
            pc.invSrcW = 1.0f / sw; pc.invSrcH = 1.0f / sh;
            pc.srcW = sw; pc.srcH = sh;
            pc.effectId = postFXMode;  
            pc.resW     = 0.0f;        
            pc.sharpness = sharpness;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        } else if (useNis) {
            WindowPushConstantsNis pc{};
            pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            float sw = (float)std::max(d.w, 1);
            float sh = (float)std::max(d.h, 1);
            pc.invSrcW = 1.0f / sw;
            pc.invSrcH = 1.0f / sh;
            pc.srcW = sw;
            pc.srcH = sh;
            pc.sharpness = sharpness;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        } else if (useLegacyUpscale) {
            WindowPushConstantsPostFX pc{};
            pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.effectId  = filterMode == 5 ? 2 : 1;
            pc.sharpness = sharpness;
            pc.resW      = (float)std::max(d.w, 1);
            pc.resH      = (float)std::max(d.h, 1);
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        } else if (usePostFX) {
            WindowPushConstantsPostFX pc{};
            pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.effectId  = postFXMode;
            pc.sharpness = sharpness;
            pc.resW      = (float)std::max(d.w, 1);
            pc.resH      = (float)std::max(d.h, 1);
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        } else if (useStretch) {
            WindowPushConstantsStretch pc{};
            pc.ndcX0 = -1.f;
            pc.ndcX1 =  1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.useTexAlpha = 0;
            pc.strength = stretchStrength;
            pc.profile  = stretchProfile;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        } else {
            WindowPushConstants pc{};
            pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.useTexAlpha = 0;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        }
        vk_.CmdDraw(cb, 4, 1, 0, 0);
    }

    if (cursorDrawn) {

        if (useSgsr || useNis || useLegacyUpscale || useStretch || usePostFX) {
            vk_.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        }
        vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &cursorDS, 0, nullptr);
        float cx=(float)std::max(0,(int)ptrX-curHotX), cy=(float)std::max(0,(int)ptrY-curHotY);
        WindowPushConstants cpc{};
        cpc.ndcX0=(ox+cx*sx)/cw*2.f-1.f; cpc.ndcY0=(oy+cy*sy)/ch*2.f-1.f;
        cpc.ndcX1=(ox+(cx+curW)*sx)/cw*2.f-1.f; cpc.ndcY1=(oy+(cy+curH)*sy)/ch*2.f-1.f;
        cpc.useTexAlpha = 1;
        vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(cpc), &cpc);
        vk_.CmdDraw(cb, 4, 1, 0, 0);
    }
    vk_.CmdEndRenderPass(cb);

    VkResult endStatus = vk_.EndCommandBuffer(cb);
    if (endStatus!=VK_SUCCESS) {
        RLOG_E("recordCmdBuf: EndCommandBuffer failed with status=%d (swapRB=%d draws=%zu)",
            (int)endStatus, (int)swapRB, draws.size());
        throw std::runtime_error("end cb");
    }
}

void VulkanRendererContext::renderLoop() {

    while (isRunning) {
        { std::unique_lock<std::mutex> lk(dirtyMutex);
          dirtyCV.wait(lk,[this]{
              return !isRunning || cursorMoved.load() ||
                  (!surfaceDetached.load() &&
                   (needsRender.load() || fbResized.load() ||
                    (frameGenMultiplier.load() >= 2 && frameGenContentDirty.load()))); }); }
        if (!isRunning) break;

        if (swapchain == VK_NULL_HANDLE || cmdBufs.empty()) continue;
        try { renderFrame(); } catch(...) {}
    }
}

void VulkanRendererContext::flushDeleteQueue() {

    std::lock_guard<std::mutex> lk(renderMutex);
    if (deleteQueue.empty()) return;
    vk_.DeviceWaitIdle(device);
    for (auto& wt:deleteQueue) {
        if (wt.ds  !=VK_NULL_HANDLE) vk_.FreeDescriptorSets(device,winTexPool,1,&wt.ds);
        if (wt.view!=VK_NULL_HANDLE) vk_.DestroyImageView(device,wt.view,nullptr);
        if (wt.img !=VK_NULL_HANDLE) vk_.DestroyImage(device,wt.img,nullptr);
        if (wt.mem !=VK_NULL_HANDLE) vk_.FreeMemory(device,wt.mem,nullptr);
        if (wt.stg !=VK_NULL_HANDLE){vk_.DestroyBuffer(device,wt.stg,nullptr);vk_.FreeMemory(device,wt.stgMem,nullptr);}
    }
    deleteQueue.clear();
}

void VulkanRendererContext::setFrameGenerationMultiplier(int multiplier) {
    int sanitized = multiplier < 2 ? 0 : std::min(4, multiplier);
    int previous = frameGenMultiplier.exchange(sanitized);
    if (previous != sanitized) {
        invalidateFrameGenHistory();
        needsRender.store(true, std::memory_order_relaxed);
        dirtyCV.notify_one();
        RLOG("Native Framegen multiplier: %s", sanitized == 0
            ? "Off" : (std::to_string(sanitized) + "x").c_str());
    }
}

void VulkanRendererContext::setFrameGenerationSmoothing(float smoothing) {
    float sanitized = std::clamp(smoothing, 0.0f, 1.0f);
    float previous = frameGenSmoothing.exchange(sanitized);
    if (previous != sanitized) {
        needsRender.store(true, std::memory_order_relaxed);
        dirtyCV.notify_one();
        RLOG("Native Framegen smoothing: %.2f", sanitized);
    }
}

void VulkanRendererContext::invalidateFrameGenHistory(bool contentDirty) {
    frameGenResetRequested.store(true, std::memory_order_release);
    if (contentDirty) frameGenContentDirty.store(true, std::memory_order_release);
}

bool VulkanRendererContext::stageFrameGenHistory(const std::vector<DrawEntry>& draws,
    VkBuffer cursorUpload, bool hasCursorUpload,
    float ox, float oy, float sx, float sy, float cw, float ch,
    short ptrX, short ptrY, short curHotX, short curHotY,
    short curW, short curH, bool curVis, VkRect2D scissorRect) {
    if (!createFrameGenResources()) return false;
    if (frameGenResetRequested.exchange(false)) {
        frameGenHistoryCount = 0;
        frameGenMotionValid = false;
    }

    for (VkFence fence : inFlightFences) {
        if (fence != VK_NULL_HANDLE) vk_.WaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX);
    }
    vk_.WaitForFences(device, 1, &frameGenStageFence, VK_TRUE, UINT64_MAX);
    vk_.ResetFences(device, 1, &frameGenStageFence);
    vk_.ResetCommandBuffer(frameGenStageCmd, 0);

    uint32_t next = frameGenHistoryCount == 0 ? 1u : (frameGenHistoryCurrent ^ 1u);
    recordCmdBuf(frameGenStageCmd, frameGenHistoryPass, frameGenHistory[next].framebuffer, draws,
        frameAhbTransitions, framePreUpload, framePostUpload,
        cursorUpload, hasCursorUpload,
        ox, oy, sx, sy, cw, ch, ptrX, ptrY, curHotX, curHotY, curW, curH, curVis,
        scissorRect);

    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &frameGenStageCmd;
    if (vk_.QueueSubmit(graphicsQueue, 1, &submit, frameGenStageFence) != VK_SUCCESS) return false;
    vk_.WaitForFences(device, 1, &frameGenStageFence, VK_TRUE, UINT64_MAX);

    frameGenHistoryCurrent = next;
    frameGenMotionValid = false;
    if (frameGenHistoryCount == 0) {
        uint32_t clone = next ^ 1u;
        VkCommandBuffer copyCmd = beginOneTime();
        transition(copyCmd, frameGenHistory[next].image,
                   VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                   VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                   VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        transition(copyCmd, frameGenHistory[clone].image,
                   VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                   0, VK_ACCESS_TRANSFER_WRITE_BIT,
                   VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        VkImageCopy copyRegion{};
        copyRegion.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        copyRegion.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        copyRegion.extent = {swapchainExt.width, swapchainExt.height, 1};
        vk_.CmdCopyImage(copyCmd,
                         frameGenHistory[next].image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                         frameGenHistory[clone].image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         1, &copyRegion);
        transition(copyCmd, frameGenHistory[next].image,
                   VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                   VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT,
                   VK_PIPELINE_STAGE_TRANSFER_BIT,
                   VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        transition(copyCmd, frameGenHistory[clone].image,
                   VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                   VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                   VK_PIPELINE_STAGE_TRANSFER_BIT,
                   VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        endOneTime(copyCmd);
        frameGenHistoryCount = 2;
    } else {
        frameGenHistoryCount = std::min(2u, frameGenHistoryCount + 1u);
    }
    return true;
}

bool VulkanRendererContext::presentFrameGenPhase(float phase, uint32_t pairCurrent,
    VkBuffer cursorUpload, bool hasCursorUpload,
    float ox, float oy, float sx, float sy, float cw, float ch,
    short ptrX, short ptrY, short curHotX, short curHotY,
    short curW, short curH, bool curVis, VkRect2D scissorRect) {
    if (!frameGenResourcesBuilt || frameGenHistoryCount < 2 || surfaceDetached.load()) return false;
    if (currentFrame >= cmdBufs.size() || cmdBufs[currentFrame] == VK_NULL_HANDLE) return false;

    bool currentFenceWaited = false;
    if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, inFlightFences[currentFrame]) == VK_NOT_READY) {
        vk_.WaitForFences(device, 1, &inFlightFences[currentFrame], VK_TRUE, UINT64_MAX);
        currentFenceWaited = true;
    }
    uint32_t imageIndex = 0;
    VkResult result = vk_.AcquireNextImageKHR(device, swapchain, UINT64_MAX,
                                               imgAvailSems[currentFrame], VK_NULL_HANDLE, &imageIndex);
    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_ERROR_SURFACE_LOST_KHR) {
        fbResized.store(true);
        return false;
    }
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) return false;
    if (imageIndex >= swapchainFBs.size()) return false;

    if (imgInFlight.size() != swapchainImages.size()) imgInFlight.assign(swapchainImages.size(), VK_NULL_HANDLE);
    if (imgInFlight[imageIndex] != VK_NULL_HANDLE &&
        (!currentFenceWaited || imgInFlight[imageIndex] != inFlightFences[currentFrame])) {
        if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, imgInFlight[imageIndex]) == VK_NOT_READY)
            vk_.WaitForFences(device, 1, &imgInFlight[imageIndex], VK_TRUE, UINT64_MAX);
    }
    imgInFlight[imageIndex] = inFlightFences[currentFrame];

    VkCommandBuffer command = cmdBufs[currentFrame];
    vk_.ResetCommandBuffer(command, 0);
    VkCommandBufferBeginInfo begin{};
    begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    if (vk_.BeginCommandBuffer(command, &begin) != VK_SUCCESS) return false;

    bool hasCursorCopy = hasCursorUpload && cursorImg != VK_NULL_HANDLE &&
                         cursorUpload != VK_NULL_HANDLE && curW > 0 && curH > 0;
    if (hasCursorCopy) {
        VkImageMemoryBarrier cursorToCopy{};
        cursorToCopy.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        cursorToCopy.oldLayout = cursorImageInitialized
            ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED;
        cursorToCopy.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        cursorToCopy.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        cursorToCopy.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        cursorToCopy.image = cursorImg;
        cursorToCopy.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        cursorToCopy.srcAccessMask = cursorImageInitialized ? VK_ACCESS_SHADER_READ_BIT : 0;
        cursorToCopy.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        vk_.CmdPipelineBarrier(command,
            cursorImageInitialized ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
            0, nullptr, 0, nullptr, 1, &cursorToCopy);
        VkBufferImageCopy copy{};
        copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        copy.imageExtent = {(uint32_t)curW, (uint32_t)curH, 1};
        vk_.CmdCopyBufferToImage(command, cursorUpload, cursorImg,
                                 VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copy);
        VkImageMemoryBarrier cursorReady = cursorToCopy;
        cursorReady.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        cursorReady.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        cursorReady.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        cursorReady.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        vk_.CmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT,
                               VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                               0, nullptr, 0, nullptr, 1, &cursorReady);
        cursorImageInitialized = true;
    }

    // Present the exact history pair captured for this output slot. This keeps
    // future asynchronous staging from changing a queued frame underneath it.
    uint32_t parity = pairCurrent & 1u;
    if (!frameGenMotionValid) {
        VkImageMemoryBarrier prepareMotion{};
        prepareMotion.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        prepareMotion.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
        prepareMotion.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        prepareMotion.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        prepareMotion.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        prepareMotion.image = frameGenMotion.image;
        prepareMotion.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        prepareMotion.srcAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
        prepareMotion.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        vk_.CmdPipelineBarrier(command,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0,
            0, nullptr, 0, nullptr, 1, &prepareMotion);
        vk_.CmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, frameGenMotionPipeline);
        vk_.CmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE,
                                  frameGenMotionPipeLayout, 0, 1,
                                  &frameGenMotionSets[parity], 0, nullptr);
        uint32_t motionWidth = std::max(1u, swapchainExt.width / 2);
        uint32_t motionHeight = std::max(1u, swapchainExt.height / 2);
        FrameGenMotionPush motionPush{
            (int32_t)motionWidth, (int32_t)motionHeight,
            1.0f / (float)motionWidth, 1.0f / (float)motionHeight,
            1.0f, 2.0f, 0.0f, 0.0f
        };
        vk_.CmdPushConstants(command, frameGenMotionPipeLayout, VK_SHADER_STAGE_COMPUTE_BIT,
                             0, sizeof(motionPush), &motionPush);
        vk_.CmdDispatch(command, (motionWidth + 7u) / 8u, (motionHeight + 7u) / 8u, 1);
        VkImageMemoryBarrier motionReady = prepareMotion;
        motionReady.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        motionReady.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        vk_.CmdPipelineBarrier(command, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                               VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                               0, nullptr, 0, nullptr, 1, &motionReady);
        frameGenMotionValid = true;
    }

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = renderPass;
    renderPassInfo.framebuffer = swapchainFBs[imageIndex];
    renderPassInfo.renderArea = {{0, 0}, swapchainExt};
    VkClearValue clear = {{{0.0f, 0.0f, 0.0f, 1.0f}}};
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clear;
    vk_.CmdBeginRenderPass(command, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
    VkViewport viewport{0, 0, (float)swapchainExt.width, (float)swapchainExt.height, 0, 1};
    VkRect2D scissor{{0, 0}, swapchainExt};
    vk_.CmdSetViewport(command, 0, 1, &viewport);
    vk_.CmdSetScissor(command, 0, 1, &scissor);
    vk_.CmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, frameGenInterpPipeline);
    vk_.CmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS,
                              frameGenInterpPipeLayout, 0, 1,
                              &frameGenInterpSets[parity], 0, nullptr);
    FrameGenInterpPush interpPush{
        (float)swapchainExt.width, (float)swapchainExt.height,
        std::clamp(phase, 0.0f, 1.0f), 0.04f, 0.18f,
        frameGenSmoothing.load(std::memory_order_relaxed)
    };
    vk_.CmdPushConstants(command, frameGenInterpPipeLayout, VK_SHADER_STAGE_FRAGMENT_BIT,
                         0, sizeof(interpPush), &interpPush);
    vk_.CmdDraw(command, 3, 1, 0, 0);

    // The hardware cursor is an overlay, not game content. Keeping it out of
    // both history images prevents cursor motion/redraws from corrupting flow.
    if (curVis && cursorImageInitialized && cursorImg != VK_NULL_HANDLE &&
        cursorDS != VK_NULL_HANDLE && curW > 0 && curH > 0 && cw > 0.f && ch > 0.f) {
        int32_t scissorX = std::max(0, scissorRect.offset.x);
        int32_t scissorY = std::max(0, scissorRect.offset.y);
        uint32_t maxW = swapchainExt.width > (uint32_t)scissorX
            ? swapchainExt.width - (uint32_t)scissorX : 0u;
        uint32_t maxH = swapchainExt.height > (uint32_t)scissorY
            ? swapchainExt.height - (uint32_t)scissorY : 0u;
        VkRect2D cursorScissor{{scissorX, scissorY},
            {std::min(scissorRect.extent.width, maxW),
             std::min(scissorRect.extent.height, maxH)}};
        vk_.CmdSetScissor(command, 0, 1, &cursorScissor);
        vk_.CmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        vk_.CmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                  pipeLayout, 0, 1, &cursorDS, 0, nullptr);
        float cursorX = (float)std::max(0, (int)ptrX - curHotX);
        float cursorY = (float)std::max(0, (int)ptrY - curHotY);
        WindowPushConstants cursorPush{};
        cursorPush.ndcX0 = (ox + cursorX * sx) / cw * 2.f - 1.f;
        cursorPush.ndcY0 = (oy + cursorY * sy) / ch * 2.f - 1.f;
        cursorPush.ndcX1 = (ox + (cursorX + curW) * sx) / cw * 2.f - 1.f;
        cursorPush.ndcY1 = (oy + (cursorY + curH) * sy) / ch * 2.f - 1.f;
        cursorPush.useTexAlpha = 1;
        vk_.CmdPushConstants(command, pipeLayout,
                             VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                             0, sizeof(cursorPush), &cursorPush);
        vk_.CmdDraw(command, 4, 1, 0, 0);
    }
    vk_.CmdEndRenderPass(command);
    if (vk_.EndCommandBuffer(command) != VK_SUCCESS) return false;

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.waitSemaphoreCount = 1;
    submit.pWaitSemaphores = &imgAvailSems[currentFrame];
    submit.pWaitDstStageMask = &waitStage;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &command;
    submit.signalSemaphoreCount = 1;
    submit.pSignalSemaphores = &renderDoneSems[currentFrame];
    vk_.ResetFences(device, 1, &inFlightFences[currentFrame]);
    if (vk_.QueueSubmit(graphicsQueue, 1, &submit, inFlightFences[currentFrame]) != VK_SUCCESS) {
        frameGenMotionValid = false;
        return false;
    }
    VkPresentInfoKHR present{};
    present.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    present.waitSemaphoreCount = 1;
    present.pWaitSemaphores = &renderDoneSems[currentFrame];
    present.swapchainCount = 1;
    present.pSwapchains = &swapchain;
    present.pImageIndices = &imageIndex;
    result = vk_.QueuePresentKHR(graphicsQueue, &present);
    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_ERROR_SURFACE_LOST_KHR) fbResized.store(true);
    currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    return result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR;
}

void VulkanRendererContext::renderFrame() {
    std::shared_lock<std::shared_mutex> frameLock(frameMutex);

    needsRender.store(false,std::memory_order_relaxed);
    cursorMoved.store(false,std::memory_order_relaxed);

    if (surfaceDetached.load(std::memory_order_acquire)) return;
    if (scanoutActive.load()) {
        applyScanoutBuffer();

        if (!scanoutBlackFrameDone.load()) {
            scanoutBlackFrameDone.store(true);

            std::lock_guard<std::mutex> lk(renderMutex);
            renderList.clear();
        } else {
            return;
        }
    } else {
        scanoutBlackFrameDone.store(false);
    }
    if (surfaceWidth==0||surfaceHeight==0) return;

    if (fbResized.load()) {
        for (auto& f:inFlightFences) vk_.WaitForFences(device,1,&f,VK_TRUE,UINT64_MAX);
        cleanupSwapchain();
        bool ok=false;
        try{createSwapchain();createFramebuffers();createCmdBufs();imgInFlight.assign(swapchainImages.size(),VK_NULL_HANDLE);
ok=true;}catch(...){}
        if (ok) fbResized.store(false);
        return;
    }

    bool frameGenHasNewContent = frameGenContentDirty.exchange(false, std::memory_order_acq_rel);

    float ox,oy,sx,sy,cw,ch;
    short ptrX,ptrY,curHotX,curHotY,curW,curH; bool curVis;
    VkBuffer curUpload=VK_NULL_HANDLE; bool hasCurUpload=false;

    VkRect2D effectiveScissor{{0,0},swapchainExt};

    {
        std::lock_guard<std::mutex> lk(renderMutex);

        if (!deleteQueue.empty()) {
            for (auto& wt:deleteQueue) {
                if (wt.ds  !=VK_NULL_HANDLE) vk_.FreeDescriptorSets(device,winTexPool,1,&wt.ds);
                if (wt.view!=VK_NULL_HANDLE) vk_.DestroyImageView(device,wt.view,nullptr);
                if (wt.img !=VK_NULL_HANDLE) vk_.DestroyImage(device,wt.img,nullptr);
                if (wt.mem !=VK_NULL_HANDLE) vk_.FreeMemory(device,wt.mem,nullptr);
                if (wt.stg !=VK_NULL_HANDLE){vk_.DestroyBuffer(device,wt.stg,nullptr);vk_.FreeMemory(device,wt.stgMem,nullptr);}
            }
            deleteQueue.clear();
        }

        ox=sceneOffsetX; oy=sceneOffsetY; sx=sceneScaleX; sy=sceneScaleY;
        cw=(float)containerWidth; ch=(float)containerHeight;
        ptrX=(short)pointerX.load(); ptrY=(short)pointerY.load();
        curHotX=cursorHotX; curHotY=cursorHotY; curW=cursorTexW; curH=cursorTexH;
        curVis=cursorVisible.load();
        if (hasCustomScissor) effectiveScissor = customScissor;

        frameDraws.clear();
        for (auto& re:renderList) {
            auto it=texMap.find(re.id);
            if (it==texMap.end()) continue;
            WinTex& wt=it->second;
            if (wt.ds==VK_NULL_HANDLE) continue;
            DrawEntry de{wt.img,wt.ds,VK_NULL_HANDLE,re.x,re.y,wt.w,wt.h};
            de.isAHB=wt.isAHB;
            if (wt.needsTransition) { de.needsTransition=true; wt.needsTransition=false; }
            if (wt.dirty && !wt.isAHB && wt.stg!=VK_NULL_HANDLE) {
                de.upload=wt.stg;
                wt.dirty=false;
            } else if (wt.isAHB) {
                wt.dirty=false;
            }
            frameDraws.push_back(de);
        }

        if (isCursorImageDirty.load() && cursorImg!=VK_NULL_HANDLE && !cursorPixels.empty()) {
            VkDeviceSize csz=(VkDeviceSize)cursorTexW*cursorTexH*4;
            ensureCursorStaging(csz);
            isCursorImageDirty.store(false); hasCurUpload=true; curUpload=cursorStg;

            cursorUploadSize = csz;
        }
    }

    if (hasCurUpload && cursorStgP && !cursorPixels.empty())
        memcpy(cursorStgP, cursorPixels.data(), cursorUploadSize);

    bool effectiveCurVis = curVis && !scanoutActive.load();
    int multiplier = frameGenMultiplier.load(std::memory_order_relaxed);
    if (multiplier >= 2 && !scanoutActive.load()) {
        bool historyReady = frameGenHistoryCount >= 2;
        if (frameGenHasNewContent || !historyReady) {
            historyReady = stageFrameGenHistory(frameDraws, curUpload, hasCurUpload,
                ox, oy, sx, sy, cw, ch, ptrX, ptrY, curHotX, curHotY, curW, curH,
                false, effectiveScissor);
            // Cursor upload was recorded into the history command, but curVis=false
            // guarantees that cursor pixels never become part of either history frame.
            hasCurUpload = false;
        }
        if (historyReady) {
            const uint32_t capturedPairCurrent = frameGenHistoryCurrent;
            // Interpolation slots lead the real frame. Presenting phase 1.0 last
            // keeps content time monotonic: 1/M, 2/M, ... 1.
            int firstPhase = frameGenHasNewContent ? 1 : multiplier;
            for (int generatedIndex = firstPhase; generatedIndex <= multiplier; generatedIndex++) {
                if (!presentFrameGenPhase((float)generatedIndex / (float)multiplier,
                    capturedPairCurrent,
                    curUpload, hasCurUpload,
                    ox, oy, sx, sy, cw, ch, ptrX, ptrY, curHotX, curHotY, curW, curH,
                    effectiveCurVis, effectiveScissor)) break;
                hasCurUpload = false;
            }
            return;
        }
    }

    if (currentFrame >= cmdBufs.size() || cmdBufs[currentFrame] == VK_NULL_HANDLE) return;
    bool currentFenceWaited = false;
    if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, inFlightFences[currentFrame]) == VK_NOT_READY) {
        vk_.WaitForFences(device,1,&inFlightFences[currentFrame],VK_TRUE,UINT64_MAX);
        currentFenceWaited = true;
    }

    uint32_t imgIdx;
    VkResult res=vk_.AcquireNextImageKHR(device,swapchain,UINT64_MAX,imgAvailSems[currentFrame],VK_NULL_HANDLE,&imgIdx);
    if (res==VK_ERROR_OUT_OF_DATE_KHR||res==VK_ERROR_SURFACE_LOST_KHR){fbResized.store(true);return;}
    if (res!=VK_SUCCESS&&res!=VK_SUBOPTIMAL_KHR) return;
    if (imgIdx >= swapchainFBs.size() || imgIdx >= swapchainImages.size()) {
        RLOG_E("renderFrame: invalid acquired image index=%u (fb=%zu images=%zu)",
            imgIdx, swapchainFBs.size(), swapchainImages.size());
        return;
    }

    if (imgInFlight.size()!=swapchainImages.size()) imgInFlight.assign(swapchainImages.size(),VK_NULL_HANDLE);
    if (imgInFlight[imgIdx]!=VK_NULL_HANDLE &&
        (!currentFenceWaited || imgInFlight[imgIdx] != inFlightFences[currentFrame])) {
        if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, imgInFlight[imgIdx]) == VK_NOT_READY) {
            vk_.WaitForFences(device,1,&imgInFlight[imgIdx],VK_TRUE,UINT64_MAX);
        }
    }
    imgInFlight[imgIdx]=inFlightFences[currentFrame];

    vk_.ResetCommandBuffer(cmdBufs[currentFrame],0);

    recordCmdBuf(cmdBufs[currentFrame],renderPass,swapchainFBs[imgIdx],frameDraws,
        frameAhbTransitions,framePreUpload,framePostUpload,
        curUpload,hasCurUpload,
        ox,oy,sx,sy,cw,ch,ptrX,ptrY,curHotX,curHotY,curW,curH,effectiveCurVis,
        effectiveScissor);

    VkSemaphore wSem[]={imgAvailSems[currentFrame]}, sSem[]={renderDoneSems[currentFrame]};
    VkPipelineStageFlags wStage[]={VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.waitSemaphoreCount=1; si.pWaitSemaphores=wSem; si.pWaitDstStageMask=wStage;
    si.commandBufferCount=1; si.pCommandBuffers=&cmdBufs[currentFrame];
    si.signalSemaphoreCount=1; si.pSignalSemaphores=sSem;

    vk_.ResetFences(device,1,&inFlightFences[currentFrame]);
    if (vk_.QueueSubmit(graphicsQueue,1,&si,inFlightFences[currentFrame])!=VK_SUCCESS) {
        vk_.DestroyFence(device,inFlightFences[currentFrame],nullptr);
        VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
        vk_.CreateFence(device,&fi,nullptr,&inFlightFences[currentFrame]);
        return;
    }
    VkSwapchainKHR scs[]={swapchain};
    VkPresentInfoKHR pi{}; pi.sType=VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    pi.waitSemaphoreCount=1; pi.pWaitSemaphores=sSem; pi.swapchainCount=1; pi.pSwapchains=scs; pi.pImageIndices=&imgIdx;
    res=vk_.QueuePresentKHR(graphicsQueue,&pi);
    if (res==VK_ERROR_OUT_OF_DATE_KHR||res==VK_ERROR_SURFACE_LOST_KHR) fbResized.store(true);
    currentFrame=(currentFrame+1)%MAX_FRAMES_IN_FLIGHT;
}

void VulkanRendererContext::onSurfaceResized(int w, int h) {
    std::lock_guard<std::mutex> lk(renderMutex);
    if (w==0||h==0) return;
    surfaceWidth=w; surfaceHeight=h;
    invalidateFrameGenHistory();
    fbResized.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::detachSurface() {
    surfaceDetached.store(true, std::memory_order_release);
    dirtyCV.notify_all();

    { std::unique_lock<std::shared_mutex> frameLock(frameMutex); }

    vk_.DeviceWaitIdle(device);
    cleanupSwapchain();
    if (surface != VK_NULL_HANDLE) {
        vk_.DestroySurfaceKHR(instance, surface, nullptr);
        surface = VK_NULL_HANDLE;
    }
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
}

bool VulkanRendererContext::reattachSurface(ANativeWindow* newWindow) {
    if (window) { ANativeWindow_release(window); window = nullptr; }
    window = newWindow;
    VkAndroidSurfaceCreateInfoKHR ci{};
    ci.sType  = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    ci.window = window;
    if (vk_.CreateAndroidSurfaceKHR(instance, &ci, nullptr, &surface) != VK_SUCCESS) {
        __android_log_print(ANDROID_LOG_ERROR, "Winlator_Renderer", "reattachSurface: CreateAndroidSurface failed");
        ANativeWindow_release(window); window = nullptr;
        return false;
    }
    {
        std::unique_lock<std::shared_mutex> frameLock(frameMutex);
        try {
            createSwapchain();
            createFramebuffers();
            createCmdBufs();
            imgInFlight.assign(swapchainImages.size(), VK_NULL_HANDLE);
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, "Winlator_Renderer", "reattachSurface: swapchain recreate failed");
            return false;
        }

        surfaceWidth  = (int)swapchainExt.width;
        surfaceHeight = (int)swapchainExt.height;

        for (auto& [id, wt] : texMap)
            wt.needsTransition = true;
        for (auto& [ahb, wt] : ahbImportCache)
            wt.needsTransition = true;

        surfaceDetached.store(false, std::memory_order_release);
    }
    invalidateFrameGenHistory();
    needsRender.store(true, std::memory_order_release);
    dirtyCV.notify_all();
    __android_log_print(ANDROID_LOG_DEBUG, "Winlator_Renderer", "reattachSurface: OK");
    return true;
}

void VulkanRendererContext::setTransform(float ox, float oy, float sx, float sy) {
    bool changed;
    { std::lock_guard<std::mutex> lk(renderMutex);
      changed = sceneOffsetX != ox || sceneOffsetY != oy || sceneScaleX != sx || sceneScaleY != sy;
      sceneOffsetX=ox;sceneOffsetY=oy;sceneScaleX=sx;sceneScaleY=sy; }
    if (changed) invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updatePointerPosition(short x, short y) {
    pointerX.store(x); pointerY.store(y);
    if (cursorVisible.load()) { cursorMoved.store(true); dirtyCV.notify_one(); }
}

void VulkanRendererContext::setCursorVisible(bool v) {
    cursorVisible.store(v); cursorMoved.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateCursorImage(void* px, short w, short h, short hotX, short hotY) {
    if (!px||w<=0||h<=0) return;
    std::lock_guard<std::mutex> lk(renderMutex);
    ensureCursorTex(w,h);
    cursorPixels.resize((size_t)w*h); memcpy(cursorPixels.data(),px,(size_t)w*h*4);
    cursorHotX=hotX; cursorHotY=hotY;
    isCursorImageDirty.store(true); needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateWindowContent(int64_t id, void* px, short w, short h, short stride, int, int) {
    if (!px||w<=0||h<=0) return;

    void* mapped=nullptr;
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        WinTex& wt=texMap[id];
        if (wt.img==VK_NULL_HANDLE || wt.w!=w || wt.h!=h) {
            if (wt.img!=VK_NULL_HANDLE) destroyWinTex(wt);
            if (!createWinTexResources(wt,w,h)) { texMap.erase(id); return; }
        }
        mapped=wt.mapped;
    }

    if (!mapped) return;
    const size_t dstPitch=(size_t)w*4;
    const int32_t srcStride=stride>0?stride:w;
    uint32_t* src2=static_cast<uint32_t*>(px);
    uint8_t*  dst2=static_cast<uint8_t*>(mapped);
    for (int row=0;row<h;++row)
        memcpy(dst2+(size_t)row*dstPitch,
               &src2[(size_t)row*srcStride],(size_t)w*4);
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        auto it=texMap.find(id);
        if (it!=texMap.end()) it->second.dirty=true;
    }
    frameGenContentDirty.store(true, std::memory_order_release);
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateWindowContentAHB(int64_t id, AHardwareBuffer* ahb, short, short, int, int) {
    if (!ahb) return;
    std::lock_guard<std::mutex> lk(renderMutex);

    auto cit = ahbImportCache.find(ahb);
    if (cit == ahbImportCache.end()) {
        WinTex tmp{};
        if (!importAHBToWinTex(tmp, ahb)) {
            RLOG_E("updateWindowContentAHB: import failed for id=%" PRId64, id);
            return;
        }
        AHardwareBuffer_acquire(ahb);
        ahbImportCache[ahb] = tmp;
        windowAhbs[id].push_back(ahb);
        cit = ahbImportCache.find(ahb);
        RLOG("updateWindowContentAHB: imported new AHB %p for id=%" PRId64 " (%dx%d)",
            (void*)ahb, id, tmp.w, tmp.h);
    }

    WinTex& src = cit->second;
    WinTex& wt  = texMap[id];
    wt.img  = src.img;
    wt.mem  = src.mem;
    wt.view = src.view;
    wt.ds   = src.ds;
    wt.isAHB = true;
    wt.ahb  = ahb;
    wt.w    = src.w;
    wt.h    = src.h;

    if (src.needsTransition) {
        wt.needsTransition  = true;
        src.needsTransition = false;
    }
    frameGenContentDirty.store(true, std::memory_order_release);
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setRenderList(const int64_t* ids, const int* xs, const int* ys, int count) {
    std::lock_guard<std::mutex> lk(renderMutex);
    bool changed = (int)renderList.size() != count;
    if (!changed) {
        for (int i=0;i<count;i++) {
            if (renderList[i].id != ids[i] || renderList[i].x != xs[i] || renderList[i].y != ys[i]) {
                changed = true;
                break;
            }
        }
    }
    renderList.resize(count);
    for (int i=0;i<count;i++) renderList[i]={ids[i],xs[i],ys[i]};
    if (changed) invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::removeWindow(int64_t id) {
    std::lock_guard<std::mutex> lk(renderMutex);

    auto it = texMap.find(id);
    if (it != texMap.end()) {
        if (!it->second.isAHB) destroyWinTex(it->second);
        else it->second = {};
        texMap.erase(it);
    }

    auto wit = windowAhbs.find(id);
    if (wit != windowAhbs.end()) {
        for (AHardwareBuffer* ahb : wit->second) {
            auto cit = ahbImportCache.find(ahb);
            if (cit != ahbImportCache.end()) {
                WinTex deferred = cit->second;
                deferred.isAHB  = false;
                deleteQueue.push_back(deferred);
                AHardwareBuffer_release(ahb);
                ahbImportCache.erase(cit);
            }
        }
        windowAhbs.erase(wit);
    }

    renderList.erase(std::remove_if(renderList.begin(),renderList.end(),
        [id](const RenderEntry& e){return e.id==id;}),renderList.end());
    invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::cleanupAllAHBCache() {
    for (auto& [ahb, wt] : ahbImportCache) {
        if (wt.ds   != VK_NULL_HANDLE) vk_.FreeDescriptorSets(device, winTexPool, 1, &wt.ds);
        if (wt.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, wt.view, nullptr);
        if (wt.img  != VK_NULL_HANDLE) vk_.DestroyImage(device, wt.img, nullptr);
        if (wt.mem  != VK_NULL_HANDLE) vk_.FreeMemory(device, wt.mem, nullptr);
        AHardwareBuffer_release(ahb);
    }
    ahbImportCache.clear();
    windowAhbs.clear();
}

void VulkanRendererContext::dumpRendererInfo() {
    VkPhysicalDeviceProperties props{};
    vk_.GetPhysicalDeviceProperties(physicalDevice,&props);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "=== RENDERER INFO ===");
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "GPU: %s vendorID=0x%x driverVersion=0x%x apiVersion=%d.%d.%d",
        props.deviceName,props.vendorID,props.driverVersion,
        VK_VERSION_MAJOR(props.apiVersion),VK_VERSION_MINOR(props.apiVersion),VK_VERSION_PATCH(props.apiVersion));
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Swapchain: %dx%d fmt=%d",swapchainExt.width,swapchainExt.height,(int)swapchainFmt);
    std::string pmList;
    for(auto pm:availablePresentModes) pmList+=std::to_string((int)pm)+" ";
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "SupportedPresentModes: [%s] current=%d",pmList.c_str(),(int)requestedPresentMode);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Filter: mode=%d (%s)", filterMode,
        filterMode==5?"DLS":filterMode==4?"FSR":filterMode==3?"NIS":
        filterMode==2?"SGSR":filterMode==1?"NEAREST":"LINEAR");
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Scanout: active=%d gameFrameDelivered=%d scanoutGameSC=%p",
        (int)scanoutActive.load(),(int)gameFrameDelivered.load(),scanoutGameSC);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Surface: %dx%d container: %dx%d",
        surfaceWidth,surfaceHeight,containerWidth,containerHeight);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,"=== END RENDERER INFO ===");
}

void VulkanRendererContext::setFilterMode(int mode) {
    // renderFrame() holds a shared frame lock while it records and submits command
    // buffers. Take the exclusive lock before replacing the sampler or rewriting
    // descriptor sets so no frame can bind the old sampler concurrently.
    std::unique_lock<std::shared_mutex> frameLock(frameMutex);
    std::lock_guard<std::mutex> renderLock(renderMutex);
    auto modeName=[](int m){ return m==5?"DLS":m==4?"FSR":m==3?"NIS":m==2?"SGSR":m==1?"NEAREST":"LINEAR"; };
    RLOG("setFilterMode: %d -> %d (%s->%s)", filterMode, mode, modeName(filterMode), modeName(mode));
    if (filterMode==mode) { RLOG("setFilterMode: already set, skipping"); return; }
    filterMode=mode;
    if (mode == 2 && sgsrPipeline == VK_NULL_HANDLE) createSgsrPipeline();
    if (mode == 3 && nisPipeline == VK_NULL_HANDLE) createNisPipeline();
    if ((mode == 4 || mode == 5) && legacyUpscalePipeline == VK_NULL_HANDLE) {
        createLegacyUpscalePipeline();
    }
    vk_.DeviceWaitIdle(device);
    if (sampler!=VK_NULL_HANDLE){vk_.DestroySampler(device,sampler,nullptr);sampler=VK_NULL_HANDLE;}
    createSampler();
    auto updateDS=[&](VkDescriptorSet ds, VkImageView view){
        if(ds==VK_NULL_HANDLE||view==VK_NULL_HANDLE) return;
        VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        dii.imageView=view; dii.sampler=sampler;
        VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        wr.dstSet=ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        wr.descriptorCount=1; wr.pImageInfo=&dii;
        vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    };

    for (auto& [id,wt]:texMap) updateDS(wt.ds, wt.view);

    for (auto& [ahb,wt]:ahbImportCache) updateDS(wt.ds, wt.view);
    if (cursorDS!=VK_NULL_HANDLE&&cursorView!=VK_NULL_HANDLE) updateDS(cursorDS, cursorView);
    invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setStretchMode(int mode) {
    RLOG("setStretchMode: %d -> %d", stretchMode, mode);
    if (stretchMode == mode) return;
    stretchMode = mode;
    if (mode == 1 && stretchPipeline == VK_NULL_HANDLE) createStretchPipeline();
    invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setSwapRB(bool enabled) {
    if (swapRB == enabled) return;
    swapRB = enabled;
    RLOG("setSwapRB: %d", (int)swapRB);
    invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setPresentMode(VkPresentModeKHR mode) {
    bool supported = false;
    for (auto pm : availablePresentModes) if (pm == mode) { supported = true; break; }
    VkPresentModeKHR target = supported ? mode : VK_PRESENT_MODE_FIFO_KHR;
    RLOG("setPresentMode: requested=%d supported=%d -> applying=%d",
        (int)mode, (int)supported, (int)target);
    if (requestedPresentMode==target) { RLOG("setPresentMode: already set, skipping"); return; }
    requestedPresentMode=target;
    fbResized.store(true); dirtyCV.notify_one();
}

std::vector<int> VulkanRendererContext::getSupportedPresentModes() const {
    std::vector<int> out;
    for (auto pm:availablePresentModes) out.push_back((int)pm);
    return out;
}

void VulkanRendererContext::setCustomScissor(int x, int y, int w, int h) {
    std::lock_guard<std::mutex> lk(renderMutex);
    VkRect2D updated = {{x, y}, {(uint32_t)std::max(0, w), (uint32_t)std::max(0, h)}};
    bool changed = !hasCustomScissor || customScissor.offset.x != updated.offset.x ||
        customScissor.offset.y != updated.offset.y ||
        customScissor.extent.width != updated.extent.width ||
        customScissor.extent.height != updated.extent.height;
    customScissor = updated;
    hasCustomScissor = true;
    if (changed) invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::clearCustomScissor() {
    std::lock_guard<std::mutex> lk(renderMutex);
    bool changed = hasCustomScissor;
    hasCustomScissor = false;
    if (changed) invalidateFrameGenHistory();
    needsRender.store(true); dirtyCV.notify_one();
}

#pragma GCC diagnostic pop
