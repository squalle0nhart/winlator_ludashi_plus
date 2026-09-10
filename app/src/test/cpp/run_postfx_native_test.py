#!/usr/bin/env python3
"""Check the real PostFX setter with Vulkan calls stubbed out (no GPU required)."""
import os
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2] /
          'main/cpp/winlator/renderer/vulkan/VulkanRendererContext.cpp').read_text()
setter = source[source.index('void VulkanRendererContext::setPostFXMode('):
                source.index('void VulkanRendererContext::setSharpness(')]
harness = r'''
#include <atomic>
#include <cassert>
#include <condition_variable>
#define RLOG(...) ((void)0)
#define VK_NULL_HANDLE 0
struct VulkanRendererContext {
    int filterMode = 0, postFXMode = 0, postfxPipeline = 0, device = 0;
    int creations = 0;
    std::atomic<bool> needsRender{false};
    std::condition_variable dirtyCV;
    struct {
        void DeviceWaitIdle(int) {}
        void DestroyPipeline(int, int, decltype(nullptr)) {}
    } vk_;
    void createPostFXPipeline() {
        if (postfxPipeline != VK_NULL_HANDLE) return;
        postfxPipeline = ++creations;
    }
    void setPostFXMode(int mode);
};
'''
checks = r'''
int main() {
    for (int filter : {0, 1, 2, 3, 4}) {
        for (int effect : {1, 2, 3, 4}) {
            VulkanRendererContext renderer;
            renderer.filterMode = filter;
            renderer.setPostFXMode(effect);
            // Every enabled effect must have a standalone pipeline ready
            // when the upscaler is switched off, including restored presets.
            assert(renderer.postFXMode == effect);
            assert(renderer.postfxPipeline != VK_NULL_HANDLE);
            assert(renderer.needsRender.load());
            renderer.setPostFXMode(effect);
            renderer.setPostFXMode(effect % 4 + 1);
            assert(renderer.creations == 1);
            renderer.setPostFXMode(0);
            assert(renderer.postFXMode == 0);
            assert(renderer.postfxPipeline == VK_NULL_HANDLE);
            renderer.setPostFXMode(effect);
            assert(renderer.postfxPipeline != VK_NULL_HANDLE);
            assert(renderer.creations == 2);
        }
    }
}
'''
with tempfile.TemporaryDirectory(prefix='postfx-native-test-') as directory:
    temp = Path(directory)
    (temp / 'test.cpp').write_text(harness + setter + checks)
    subprocess.run([os.environ.get('CXX', 'c++'), '-std=c++17',
                    str(temp / 'test.cpp'), '-o', str(temp / 'test')], check=True)
    subprocess.run([str(temp / 'test')], check=True)
print('PostFX pipeline checks passed for all filter and effect modes')
