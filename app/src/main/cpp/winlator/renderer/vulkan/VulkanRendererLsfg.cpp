// LSFG compositor integration adapted from Bannerlator 63af72a (GPL-3.0-or-later).
#include "VulkanRendererContext.h"
#include "lsfg/lsfg_engine.h"
#include "lsfg/lsfg_vkd.h"
#include "winfg/winfg_engine.h"

#ifndef WINFG_UPSTREAM
#define WINFG_UPSTREAM "unknown"
#endif
#ifndef WINFG_CHAIN_HASH
#define WINFG_CHAIN_HASH "unknown"
#endif

VkRenderPass VulkanRendererContext::createCompatibleRenderPass(
    VkAttachmentLoadOp loadOp, VkImageLayout initialLayout, VkImageLayout finalLayout) {

    // All three passes share attachment format, subpass and dependencies so
    // existing pipelines/framebuffers remain compatible. Only ops/layouts vary.
    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=loadOp; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=initialLayout;
    att.finalLayout=finalLayout;

    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;

    VkSubpassDependency deps[2]{};
    // A previous frame may still be sampling or computing on this target.
    deps[0].srcSubpass=VK_SUBPASS_EXTERNAL; deps[0].dstSubpass=0;
    deps[0].srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT;
    deps[0].dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[0].srcAccessMask=VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_TRANSFER_READ_BIT|VK_ACCESS_TRANSFER_WRITE_BIT|VK_ACCESS_SHADER_WRITE_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[0].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    // Make this pass's writes visible to the copy and to the LSFG chain.
    deps[1].srcSubpass=0; deps[1].dstSubpass=VK_SUBPASS_EXTERNAL;
    deps[1].srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[1].dstStageMask=VK_PIPELINE_STAGE_TRANSFER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    deps[1].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT|VK_ACCESS_SHADER_READ_BIT;

    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=2; ci.pDependencies=deps;
    VkRenderPass result = VK_NULL_HANDLE;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&result)!=VK_SUCCESS)
        RLOG_E("createCompatibleRenderPass failed");
    return result;
}

bool VulkanRendererContext::createCompositeRenderPass() {
    if (!compositeRenderPass)
        compositeRenderPass = createCompatibleRenderPass(VK_ATTACHMENT_LOAD_OP_CLEAR,
            VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL);
    return compositeRenderPass != VK_NULL_HANDLE;
}

bool VulkanRendererContext::ensureCompositeTargets(uint32_t w, uint32_t h, uint32_t count) {
    if (w == 0 || h == 0 || count == 0) return false;
    if (count > kMaxCompositeTargets) count = kMaxCompositeTargets;

    if (compositeW == w && compositeH == h && compositeTargets.size() == count) return true;
    if (!createCompositeRenderPass()) return false;

    // Changing the multiplier resizes the ring, and the images being replaced
    // may still be referenced by a submitted command buffer. Destroying them
    // under the GPU is a use-after-free; wait first.
    if (!compositeTargets.empty()) vk_.DeviceWaitIdle(device);
    if (lsfgEngine_) lsfgEngine_->forgetTargets();
    destroyCompositeTargets();

    compositeTargets.resize(count);
    for (uint32_t i = 0; i < count; i++) {
        CompositeTarget& t = compositeTargets[i];

        VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        ii.imageType=VK_IMAGE_TYPE_2D; ii.extent={w,h,1};
        ii.mipLevels=1; ii.arrayLayers=1; ii.format=swapchainFmt;
        ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        // The full set the frame-gen path needs: drawn into by the effect
        // chain, sampled as the next frame's LSFG input, written by `generate`
        // through a storage view, and copied into the swapchain image.
        ii.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                 | VK_IMAGE_USAGE_SAMPLED_BIT
                 | VK_IMAGE_USAGE_STORAGE_BIT
                 | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                 | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        if (vk_.CreateImage(device,&ii,nullptr,&t.img)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,t.img,&req);
        VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        ai.allocationSize=req.size;
        ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (vk_.AllocateMemory(device,&ai,nullptr,&t.mem)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }
        if (vk_.BindImageMemory(device,t.img,t.mem,0)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkImageViewCreateInfo vci{}; vci.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        vci.image=t.img; vci.viewType=VK_IMAGE_VIEW_TYPE_2D; vci.format=swapchainFmt;
        vci.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        vci.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
        if (vk_.CreateImageView(device,&vci,nullptr,&t.view)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }
        // Storage views must carry identity swizzle and no component mapping;
        // same descriptor otherwise.
        if (vk_.CreateImageView(device,&vci,nullptr,&t.storageView)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkImageView att[]={t.view};
        VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fi.renderPass=compositeRenderPass; fi.attachmentCount=1; fi.pAttachments=att;
        fi.width=w; fi.height=h; fi.layers=1;
        if (vk_.CreateFramebuffer(device,&fi,nullptr,&t.fb)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
        if (vk_.AllocateDescriptorSets(device,&dsai,&t.ds)!=VK_SUCCESS) { t.ds=VK_NULL_HANDLE; destroyCompositeTargets(); return false; }
        VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_GENERAL;
        dii.imageView=t.view; dii.sampler=sampler;
        VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        wr.dstSet=t.ds; wr.dstBinding=0;
        wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1;
        wr.pImageInfo=&dii;
        vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    }

    compositeW = w; compositeH = h; compositeIndex = 0;
    RLOG("composite ring: %ux%u x%u targets", w, h, count);
    return true;
}

void VulkanRendererContext::destroyCompositeTargets() {
    for (CompositeTarget& t : compositeTargets) {
        if (t.ds          !=VK_NULL_HANDLE){ vk_.FreeDescriptorSets(device,winTexPool,1,&t.ds); t.ds=VK_NULL_HANDLE; }
        if (t.fb          !=VK_NULL_HANDLE){ vk_.DestroyFramebuffer(device,t.fb,nullptr); t.fb=VK_NULL_HANDLE; }
        if (t.storageView !=VK_NULL_HANDLE){ vk_.DestroyImageView(device,t.storageView,nullptr); t.storageView=VK_NULL_HANDLE; }
        if (t.view        !=VK_NULL_HANDLE){ vk_.DestroyImageView(device,t.view,nullptr); t.view=VK_NULL_HANDLE; }
        if (t.img         !=VK_NULL_HANDLE){ vk_.DestroyImage(device,t.img,nullptr); t.img=VK_NULL_HANDLE; }
        if (t.mem         !=VK_NULL_HANDLE){ vk_.FreeMemory(device,t.mem,nullptr); t.mem=VK_NULL_HANDLE; }
    }
    compositeTargets.clear();
    compositeW = compositeH = 0;
    compositeIndex = 0;
}

// ===================== Native LSFG: generation passes ========================

bool VulkanRendererContext::ensureLsfgEngine() {
    if (lsfgEngine_) return lsfgEngine_->valid();
    if (lsfgEngineTried_) return false;      // failed once; don't retry every frame
    lsfgEngineTried_ = true;

    if (lsfgCachePath_.empty()) {
        RLOG_E("lsfg-native: no shader cache path set");
        return false;
    }
    if (!lsfgVkdInit(vk_)) {
        RLOG_E("lsfg-native: dispatch incomplete; frame generation unavailable");
        return false;
    }

    auto engine = std::make_unique<lsfg::Engine>();
    if (!engine->init(device, physicalDevice, lsfgCachePath_)) {
        RLOG_E("lsfg-native: engine init failed (cache %s)", lsfgCachePath_.c_str());
        return false;
    }
    lsfgEngine_ = std::move(engine);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
    RLOG("lsfg-native: engine ready");
    return true;
}

bool VulkanRendererContext::ensureWinFgEngine() {
    if (winfgEngine_) return winfgEngine_->valid();
    if (winfgEngineTried_) return false;
    winfgEngineTried_ = true;

    auto engine = std::make_unique<winfg::Engine>();
    if (!engine->init(vk_, physicalDevice, device, graphicsQueueFamilyIndex, graphicsQueue)) {
        RLOG_E("winfg-native: engine init failed");
        return false;
    }
    winfgEngine_ = std::move(engine);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
    RLOG("winfg-native: engine ready (chain %s, src %s)", WINFG_UPSTREAM, WINFG_CHAIN_HASH);
    return true;
}

bool VulkanRendererContext::fgCapsOk() const {
    return fgEngineKind_.load(std::memory_order_relaxed) == 1
        ? lsfgCaps_.storageOnSwapchainFormat
        : lsfgCaps_.supported();
}

void VulkanRendererContext::setFrameGenEngine(int kind) {
    std::lock_guard<std::mutex> lk(renderMutex);
    kind = kind == 1 ? 1 : 0;
    const int was = fgEngineKind_.exchange(kind, std::memory_order_relaxed);
    if (was == kind) return;
    vk_.DeviceWaitIdle(device);
    lsfgEngine_.reset();
    lsfgEngineTried_ = false;
    winfgEngine_.reset();
    winfgEngineTried_ = false;
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

void VulkanRendererContext::setWinFgTuning(int model, int perfPreset) {
    fgModel_.store(model, std::memory_order_relaxed);
    fgPerfPreset_.store(perfPreset, std::memory_order_relaxed);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

void VulkanRendererContext::ensureFgQueryPool() {
    if (fgQueryPool_ != VK_NULL_HANDLE || !fgTimestampsOk_ || !vk_.CreateQueryPool) return;
    VkQueryPoolCreateInfo qi{}; qi.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qi.queryType = VK_QUERY_TYPE_TIMESTAMP; qi.queryCount = MAX_FRAMES_IN_FLIGHT * 2;
    if (vk_.CreateQueryPool(device, &qi, nullptr, &fgQueryPool_) != VK_SUCCESS) {
        fgQueryPool_ = VK_NULL_HANDLE;
        fgTimestampsOk_ = false;
        RLOG_E("lsfg-native: timestamp query pool unavailable; chain cost will not be reported");
    }
}

void VulkanRendererContext::destroyFgQueryPool() {
    if (fgQueryPool_ != VK_NULL_HANDLE && vk_.DestroyQueryPool)
        vk_.DestroyQueryPool(device, fgQueryPool_, nullptr);
    fgQueryPool_ = VK_NULL_HANDLE;
    for (auto& p : fgQueryPending_) p = false;
}

void VulkanRendererContext::readFgQueryResult() {
    if (fgQueryPool_ == VK_NULL_HANDLE || !fgQueryPending_[currentFrame]) return;
    fgQueryPending_[currentFrame] = false;      // one attempt per sample; a miss is dropped
    uint64_t ts[2] = {0, 0};
    const VkResult r = vk_.GetQueryPoolResults(device, fgQueryPool_, currentFrame * 2, 2,
                                               sizeof(ts), ts, sizeof(uint64_t),
                                               VK_QUERY_RESULT_64_BIT);
    if (r != VK_SUCCESS || ts[1] <= ts[0]) return;
    const double ms = (double)(ts[1] - ts[0]) * (double)fgTimestampPeriodNs_ / 1.0e6;
    const float perGen = (float)(ms / (double)std::max(1u, fgQueryGens_[currentFrame]));
    fgChainMsPerGen_ = fgChainMsPerGen_ < 0.0f
        ? perGen
        : fgChainMsPerGen_ + (perGen - fgChainMsPerGen_) * 0.1f;
    // Same cadence as the engine's own telemetry, so a log carries the cost
    // next to the rates it explains.
    if ((fgChainLogCount_++ % 120u) == 0u)
        RLOG("lsfg-native: chain %.2f ms per generated frame (%.2f ms for %u, smoothed %.2f)",
             perGen, (float)ms, fgQueryGens_[currentFrame], fgChainMsPerGen_);
}

void VulkanRendererContext::recordFrameGenProcess(VkCommandBuffer cb) {
    const bool useWinFg = fgEngineKind_.load(std::memory_order_relaxed) == 1;
    if (!compositeActive() || compositeTargets.empty()) return;
    if (useWinFg ? !winfgEngine_ : !lsfgEngine_) return;
    const CompositeTarget& src = compositeTargets[compositeIndex];
    ensureFgQueryPool();
    if (fgQueryPool_ != VK_NULL_HANDLE && fgPlan_.generations > 0) {
        vk_.CmdResetQueryPool(cb, fgQueryPool_, currentFrame * 2, 2);
        vk_.CmdWriteTimestamp(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, fgQueryPool_, currentFrame * 2);
    }
    // Take frame N as the chain's newest input and run everything that is
    // shared across generations (mipmaps -> alpha -> beta -> gamma -> delta).
    if (useWinFg) winfgEngine_->process(cb, src.img, compositeW, compositeH, fgPlan_.generations);
    else          lsfgEngine_->process(cb, src.img, compositeW, compositeH, fgPlan_.generations);
}

void VulkanRendererContext::recordFrameGenGeneration(VkCommandBuffer cb, uint32_t g) {
    const bool useWinFg = fgEngineKind_.load(std::memory_order_relaxed) == 1;
    if (!compositeActive() || compositeTargets.empty()) return;
    if (useWinFg ? !winfgEngine_ : !lsfgEngine_) return;
    if (g >= fgPlan_.generations) return;
    const uint32_t w = compositeW, h = compositeH;
    {
        const size_t slot = (compositeIndex + 1 + g) % compositeTargets.size();
        const CompositeTarget& dst = compositeTargets[slot];
        if (dst.img == VK_NULL_HANDLE || dst.storageView == VK_NULL_HANDLE) return;

        if (useWinFg) winfgEngine_->generateInto(cb, g, fgPlan_.generations,
                                                  dst.img, dst.storageView, w, h);
        else          lsfgEngine_->generateInto(cb, g, (uint32_t)slot,
                                                 dst.img, dst.storageView, w, h);

        if (fgQueryPool_ != VK_NULL_HANDLE && g + 1 == fgPlan_.generations) {
            vk_.CmdWriteTimestamp(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, fgQueryPool_, currentFrame * 2 + 1);
            fgQueryPending_[currentFrame] = true;
            fgQueryGens_[currentFrame]    = fgPlan_.generations;
        }

        // dst is left in GENERAL by the chain; move it and the target
        // swapchain image into transfer layouts, copy, then hand the swapchain
        // image to the presentation engine.
        const uint32_t imgIdx = fgPlan_.imgIdx[g];
        VkImageMemoryBarrier pre[2]{};
        pre[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        pre[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; pre[0].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        pre[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        pre[0].image=dst.img; pre[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        pre[0].srcAccessMask=VK_ACCESS_SHADER_WRITE_BIT; pre[0].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;

        pre[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        pre[1].oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; pre[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        pre[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        pre[1].image=swapchainImages[imgIdx]; pre[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        pre[1].srcAccessMask=0; pre[1].dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;

        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr, 0,nullptr, 2,pre);

        VkImageCopy region{};
        region.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        region.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        region.extent={w,h,1};
        vk_.CmdCopyImage(cb, dst.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                         swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         1, &region);

        // Restore the generation target first; the swapchain image's own final
        // transition depends on whether the cursor overlay runs for it.
        VkImageMemoryBarrier post[2]{};
        post[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        post[0].oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; post[0].newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        post[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        post[0].image=swapchainImages[imgIdx]; post[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        post[0].srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; post[0].dstAccessMask=0;
        // Put the generation target back in GENERAL for the next dispatch.
        post[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        post[1].oldLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL; post[1].newLayout=VK_IMAGE_LAYOUT_GENERAL;
        post[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        post[1].image=dst.img; post[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        post[1].srcAccessMask=VK_ACCESS_TRANSFER_READ_BIT; post[1].dstAccessMask=VK_ACCESS_SHADER_WRITE_BIT;

        if (cursorDrawnPerPresent() && cursorOverlay_.draw) {
            // Only the generation target goes back to GENERAL here; the
            // overlay pass takes the swapchain image to PRESENT_SRC.
            vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0,nullptr, 0,nullptr, 1,&post[1]);
            recordCursorOverlay(cb, imgIdx);
        } else {
            vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0, 0,nullptr, 0,nullptr, 2,post);
        }
    }
}

void VulkanRendererContext::setLsfgCachePath(const char* path) {
    std::unique_lock<std::shared_mutex> frameLock(frameMutex);
    std::lock_guard<std::mutex> lk(renderMutex);
    const std::string next = path ? path : "";
    // Only a genuinely NEW cache justifies throwing the engine away. This is
    // called on every multiplier change, and rebuilding here meant the chain,
    // the pacer's rate history and the governor's accepted level were all
    // discarded each time - so the governor restarted at zero generations and
    // never survived long enough to probe. Frame generation reported gen=0
    // forever while looking perfectly healthy.
    if (next == lsfgCachePath_ && lsfgEngine_) return;
    lsfgCachePath_ = next;
    lsfgEngineTried_ = false;    // a new cache deserves a fresh attempt
    vk_.DeviceWaitIdle(device);
    lsfgEngine_.reset();
}

void VulkanRendererContext::setFrameGenTuning(float flowScale, float refreshHz) {
    fgFlowScale_.store(flowScale, std::memory_order_relaxed);
    // A zero here means the display could not be read, not "0 Hz". Keeping the
    // last good value stops the pacer's refresh ceiling flapping between 144
    // and unknown, which was visible in the logs as max= alternating.
    if (refreshHz > 1.0f) fgRefreshHz_.store(refreshHz, std::memory_order_relaxed);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

// ================== Native LSFG: per-present software cursor =================
// LSFG warps whatever it is handed along the estimated flow field. A cursor
// composited into the frame is therefore smeared across the generated frames,
// which is very visible on a Wine desktop. So while frame gen is armed the
// cursor is left OUT of the composite and drawn once into every presented
// image - real and generated alike - by a load-op pass that also performs the
// final transition to PRESENT_SRC.

bool VulkanRendererContext::cursorDrawnPerPresent() const {
    return compositeActive() && cursorOverlayRenderPass != VK_NULL_HANDLE;
}

bool VulkanRendererContext::createCursorOverlayRenderPass() {
    if (!cursorOverlayRenderPass)
        cursorOverlayRenderPass = createCompatibleRenderPass(VK_ATTACHMENT_LOAD_OP_LOAD,
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
    return cursorOverlayRenderPass != VK_NULL_HANDLE;
}

void VulkanRendererContext::recordCursorOverlay(VkCommandBuffer cb, uint32_t imgIdx) {
    if (!cursorDrawnPerPresent()) return;
    const CursorOverlay& c = cursorOverlay_;
    if (!c.draw || cursorDS == VK_NULL_HANDLE || imgIdx >= swapchainFBs.size()) return;

    // The copy left the image in TRANSFER_DST; the render pass wants
    // COLOR_ATTACHMENT_OPTIMAL and hands it on as PRESENT_SRC itself.
    VkImageMemoryBarrier toAttachment{};
    toAttachment.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    toAttachment.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toAttachment.newLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    toAttachment.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    toAttachment.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    toAttachment.image=swapchainImages[imgIdx];
    toAttachment.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    toAttachment.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
    toAttachment.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, 0,nullptr, 0,nullptr, 1,&toAttachment);

    VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rpi.renderPass=cursorOverlayRenderPass; rpi.framebuffer=swapchainFBs[imgIdx];
    rpi.renderArea={{0,0},swapchainExt};
    rpi.clearValueCount=0; rpi.pClearValues=nullptr;   // LOAD_OP_LOAD: nothing to clear
    vk_.CmdBeginRenderPass(cb,&rpi,VK_SUBPASS_CONTENTS_INLINE);

    VkViewport vp{0,0,(float)swapchainExt.width,(float)swapchainExt.height,0,1};
    VkRect2D   sc{{0,0},swapchainExt};
    vk_.CmdSetViewport(cb,0,1,&vp);
    vk_.CmdSetScissor(cb,0,1,&sc);
    // `pipeline` is the alpha-blended window pipeline, built against renderPass;
    // format compatibility is what makes it usable with the overlay pass too.
    vk_.CmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
    vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeLayout,0,1,&cursorDS,0,nullptr);

    const float cx=(float)((int)c.ptrX-c.hotX), cy=(float)((int)c.ptrY-c.hotY);
    WindowPushConstants pc{};
    pc.ndcX0=(c.ox+cx*c.sx)/c.cw*2.f-1.f;
    pc.ndcY0=(c.oy+cy*c.sy)/c.ch*2.f-1.f;
    pc.ndcX1=(c.ox+(cx+c.w)*c.sx)/c.cw*2.f-1.f;
    pc.ndcY1=(c.oy+(cy+c.h)*c.sy)/c.ch*2.f-1.f;
    pc.useTexAlpha=1;
    getPreRotationCosSin(pc.cosR, pc.sinR);
    vk_.CmdPushConstants(cb,pipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,
                         0,sizeof(pc),&pc);
    vk_.CmdDraw(cb,4,1,0,0);
    vk_.CmdEndRenderPass(cb);
}

void VulkanRendererContext::frameGenStats(float out[6]) const {
    out[0] = out[1] = out[2] = 0.0f;
    out[4] = -1.0f;
    // The presented rate is measured by the renderer itself, per present, and is
    // valid for EVERY engine - including win-fg, whose generated frames arrive
    // from inside the guest. It is reported even with no LSFG engine present.
    out[3] = fgPresentedRate_;
    out[5] = fgChainMsPerGen_;
    if (fgEngineKind_.load(std::memory_order_relaxed) == 1) {
        if (!winfgEngine_) return;
        out[0] = out[1] = (float)fgPlan_.generations;
        out[2] = fgSourceRate_;
        return;
    }
    if (!lsfgEngine_) return;
    out[0] = (float)lsfgEngine_->acceptedGenerations();
    out[1] = (float)fgPlan_.generations;
    out[2] = lsfgEngine_->sourceRate();
    out[4] = (float)lsfgEngine_->thermalStatus();
}

void VulkanRendererContext::trackPresentedRate(uint32_t presents) {
    const auto now = std::chrono::steady_clock::now();
    if (!fgRateWindowOpen_) {
        fgRateWindowStart_ = now;
        fgRateWindowOpen_  = true;
        fgPresentAccum_    = 0;
        fgSourceAccum_     = 0;
    }
    fgPresentAccum_ += presents;
    fgSourceAccum_++;

    const float elapsed = std::chrono::duration<float>(now - fgRateWindowStart_).count();
    if (elapsed < 0.5f) return;                    // half-second window
    const float rate = (float)fgPresentAccum_ / elapsed;
    // Same smoothing shape the pacer uses, so the two numbers are comparable.
    fgPresentedRate_ = fgPresentedRate_ > 0.0f
        ? fgPresentedRate_ + (rate - fgPresentedRate_) * 0.25f
        : rate;
    const float sourceRate = (float)fgSourceAccum_ / elapsed;
    fgSourceRate_ = fgSourceRate_ > 0.0f
        ? fgSourceRate_ + (sourceRate - fgSourceRate_) * 0.25f
        : sourceRate;
    fgRateWindowStart_ = now;
    fgPresentAccum_    = 0;
    fgSourceAccum_     = 0;
}

void VulkanRendererContext::setFrameGenArmed(bool armed, int multiplier) {
    std::unique_lock<std::shared_mutex> frameLock(frameMutex);
    const bool was = fgArmed_.load(std::memory_order_relaxed);
    const int  wasMult = fgMultiplier_.load(std::memory_order_relaxed);
    fgMultiplier_.store(multiplier, std::memory_order_relaxed);
    // A multiplier change must reach the pacer, or it keeps capping at the
    // level it was built with.
    if (wasMult != multiplier) {
        fgConfigDirty_.store(true, std::memory_order_relaxed);
        // Logged because a multiplier change that does NOT flip the armed state
        // takes the early return below and was previously invisible - exactly
        // the case the r3 run could not explain.
        RLOG("lsfg-native: multiplier %d -> %d (armed=%d)", wasMult, multiplier, (int)armed);
    }
    if (was == armed) return;

    fgArmed_.store(armed, std::memory_order_relaxed);
    RLOG("lsfg-native: frame gen %s (multiplier=%d) - recreating swapchain",
         armed ? "ARMED" : "disarmed", multiplier);
    // The swapchain's usage flags and image count both depend on this, so it
    // has to be rebuilt. The existing resize path already does that safely.
    fbResized.store(true);
    dirtyCV.notify_one();
}

bool VulkanRendererContext::compositeActive() const {
    // Every gate must hold, or we run the pre-LSFG path unchanged.
    if (!fgArmed_.load(std::memory_order_relaxed)) return false;
    if (!fgCapsOk()) return false;
    return compositeArmed;
}

VkRenderPass VulkanRendererContext::targetRenderPass() const {
    return compositeActive() ? compositeRenderPass : renderPass;
}

VkFramebuffer VulkanRendererContext::targetFramebuffer(uint32_t imgIdx) const {
    if (compositeActive() && compositeIndex < compositeTargets.size())
        return compositeTargets[compositeIndex].fb;
    return swapchainFBs[imgIdx];
}

void VulkanRendererContext::copyCompositeToSwapchain(VkCommandBuffer cb, uint32_t imgIdx) {
    if (!compositeActive() || compositeIndex >= compositeTargets.size()) return;
    const CompositeTarget& t = compositeTargets[compositeIndex];

    // composite: GENERAL (render pass left it there) -> TRANSFER_SRC
    // swapchain: UNDEFINED (nothing has touched it this frame) -> TRANSFER_DST
    VkImageMemoryBarrier pre[2]{};
    pre[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; pre[0].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    pre[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    pre[0].image=t.img; pre[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    pre[0].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT|VK_ACCESS_SHADER_WRITE_BIT;
    pre[0].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;

    pre[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[1].oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; pre[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    pre[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    pre[1].image=swapchainImages[imgIdx]; pre[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    pre[1].srcAccessMask=0; pre[1].dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;

    vk_.CmdPipelineBarrier(cb,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr, 0,nullptr, 2,pre);

    // Same format and same extent, so a copy is enough — no blit, no filtering.
    VkImageCopy region{};
    region.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    region.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    region.extent={compositeW, compositeH, 1};
    vk_.CmdCopyImage(cb,
        t.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        1, &region);

    // The cursor overlay, when it runs, takes the image from TRANSFER_DST to
    // PRESENT_SRC itself; only do it here when there is no overlay.
    if (cursorDrawnPerPresent() && cursorOverlay_.draw) {
        recordCursorOverlay(cb, imgIdx);
        return;
    }

    VkImageMemoryBarrier post{};
    post.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    post.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; post.newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    post.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    post.image=swapchainImages[imgIdx]; post.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    post.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; post.dstAccessMask=0;
    vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0,nullptr, 0,nullptr, 1,&post);
}
