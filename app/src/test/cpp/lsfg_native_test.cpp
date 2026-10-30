#include "lsfg_pacer.hpp"
#include "lsfg_capture.h"
#include "lsfg_dll.h"
#include "lsfg_dxbc.h"

#include <cassert>
#include <cstring>
#include <fstream>
#include <vector>

void runProbeChecks();

int main() {
    runProbeChecks();
    assert(lsfg::captureExtent(2712, 1220, 720).width == 1600);
    assert(lsfg::captureExtent(2712, 1220, 720).height == 720);
    assert(lsfg::captureExtent(1920, 1080, 720).width == 1280);
    assert(lsfg::captureExtent(1920, 1080, 1080).height == 1080);
    assert(lsfg::captureExtent(2400, 1080, 100).height == 270);
    assert(lsfg::captureExtent(2400, 1080, 0).height == 1080);

    lsfg::LsfgPacer pacer;
    for (uint32_t multiplier : {0u, 1u, 2u, 3u, 4u, 99u}) {
        pacer.SetConfig({multiplier, 0, 60.0f});
        const size_t expected = multiplier < 2 ? 0 : std::min(multiplier, 4u) - 1;
        assert(pacer.MaxGenerations() == expected);
        assert(pacer.Plan(0, 100).generations == 0);
        assert(pacer.Plan(3, 101).generations == 0); // First frame seeds history.
        assert(pacer.Plan(1, 102).generations <= std::min(expected, size_t{1}));
        pacer.Reset();
        assert(pacer.Stats().source_frames == 0);
    }

    // Loading a cache must lower only the in-memory module, retaining memory-model declarations.
    const std::vector<uint32_t> spirv{0x07230203u, lsfg::kSpirv16, 0, 2, 0,
        (2u << 16) | 17u, 5345u, (3u << 16) | 14u, 0, 3};
    auto lowered = spirv;
    assert(lsfg::downgradeSpirv(lowered, lsfg::kSpirv14));
    assert(lowered[1] == lsfg::kSpirv14 && (lowered[7] & 0xffffu) == 10u);
    assert(std::strcmp(reinterpret_cast<const char*>(&lowered[8]), "SPV_KHR_vulkan_memory_model") == 0);
    const auto once = lowered;
    assert(lsfg::downgradeSpirv(lowered, lsfg::kSpirv14) && lowered == once);
    lowered = spirv;
    assert(lsfg::downgradeSpirv(lowered, lsfg::kSpirv15));
    assert(lowered.size() == spirv.size() && lowered[1] == lsfg::kSpirv15);
    auto demote = spirv;
    demote[6] = 5379u;
    lowered = demote;
    assert(!lsfg::downgradeSpirv(lowered, lsfg::kSpirv14) && lowered == demote);
    lowered = spirv;
    lowered[5] = 0; // Zero-length instruction is rejected without modifying the module.
    const auto malformed = lowered;
    assert(!lsfg::downgradeSpirv(lowered, lsfg::kSpirv14) && lowered == malformed);
    assert(!lsfg::downgradeSpirv(lowered, 0x00010300u));

    using lsfg::DllStatus;
    assert(lsfg::validateDll("missing.dll") == DllStatus::NotInstalled);
    for (size_t size : {1u, 2u, 63u, 64u, 128u, 512u}) {
        std::vector<char> bad(size, '\xff');
        if (size >= 2) { bad[0] = 'M'; bad[1] = 'Z'; }
        std::ofstream("invalid.dll", std::ios::binary).write(bad.data(), bad.size());
        assert(lsfg::validateDll("invalid.dll") != DllStatus::Ok);
        assert(lsfg::dllVariant("invalid.dll", false) == lsfg::Variant::None);
        assert(lsfg::buildCache("invalid.dll", "cache", false) != DllStatus::Ok);
        lsfg::ModuleSet modules;
        assert(lsfg::loadModules("invalid.dll", modules) == DllStatus::CacheUnusable);
    }
    // Malformed shader bytecode must return an error without escaping an exception.
    std::vector<uint32_t> words{123};
    const uint8_t badDxbc[] = {'D', 'X', 'B', 'C'};
    assert(!lsfg::translateDxbc(badDxbc, sizeof(badDxbc), words));
    assert(words.empty());
    assert(lsfg::shaderIds().size() == lsfg::kShaderCount);
}
