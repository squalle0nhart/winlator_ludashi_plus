#!/usr/bin/env python3
"""Run the real LSFG parser, DXBC translator and pacer on the host, without Android."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

main = Path(__file__).resolve().parents[2] / 'main' / 'cpp'
engine = main / 'winlator/renderer/vulkan/lsfg'
dxbc = main / 'thirdparty/dxbc'
ndk_headers = next(Path(sys.argv[1]).glob('toolchains/llvm/prebuilt/*/sysroot/usr/include'))
with tempfile.TemporaryDirectory(prefix='lsfg-native-test-') as directory:
    temp = Path(directory)
    for name in ('vulkan', 'vk_video'):
        (temp / name).symlink_to(ndk_headers / name, target_is_directory=True)
    (temp / 'android').mkdir()
    (temp / 'android/log.h').write_text('''#pragma once
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_ERROR 6
inline int __android_log_print(int, const char*, const char*, ...) { return 0; }
''')
    # The real capability probe only needs these three dispatch entries, not Android's renderer.
    (temp / 'probe_vk_table.h').write_text('''#pragma once
#include <vulkan/vulkan.h>
struct VkTable {
    PFN_vkGetPhysicalDeviceProperties GetPhysicalDeviceProperties;
    PFN_vkGetPhysicalDeviceFeatures2 GetPhysicalDeviceFeatures2;
    PFN_vkGetPhysicalDeviceFormatProperties GetPhysicalDeviceFormatProperties;
};
''')
    probe = (engine / 'lsfg_probe.cpp').read_text().replace(
        '#include "../VulkanRendererContext.h"', '#include "probe_vk_table.h"')
    (temp / 'lsfg_probe.cpp').write_text(probe)
    sources = [Path(__file__).with_name('lsfg_native_test.cpp'),
               Path(__file__).with_name('lsfg_probe_test.cpp'), temp / 'lsfg_probe.cpp']
    sources += [engine / name for name in ('lsfg_pacer.cpp', 'lsfg_dll.cpp', 'lsfg_dxbc.cpp')]
    sources += sorted((dxbc / 'src').glob('*/*.cpp'))
    includes = [temp, engine] + [dxbc / 'include' / name for name in ('dxbc', 'spirv', 'util', 'dxvk')]
    command = [os.environ.get('CXX', 'c++'), '-std=c++17', '-fsanitize=address,undefined', '-g']
    command += ['-I' + str(path) for path in includes]
    command += [str(path) for path in sources] + ['-o', str(temp / 'lsfg-native-test')]
    subprocess.run(command, check=True)
    subprocess.run([str(temp / 'lsfg-native-test')], cwd=temp, check=True)
print('LSFG parser, translator, pacer, SPIR-V lowering, and capability probe checks passed')
