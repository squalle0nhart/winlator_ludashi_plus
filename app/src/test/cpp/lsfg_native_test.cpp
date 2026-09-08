#include "lsfg_pacer.hpp"
#include "lsfg_dll.h"
#include "lsfg_dxbc.h"

#include <cassert>
#include <fstream>
#include <vector>

int main() {
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
