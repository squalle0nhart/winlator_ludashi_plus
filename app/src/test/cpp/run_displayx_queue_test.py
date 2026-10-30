#!/usr/bin/env python3
"""Exercise the actual DisplayX queue without Android headers or a device."""
import os
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2] / 'main/cpp/winlator/renderer/displayx.hpp').read_text()
request = source[source.index('        struct PresentRequest {'):source.index('        class PresentQueue {')]
queue = source[source.index('        class PresentQueue {'):source.index('        struct OnCompleteContext {')]
implementation = (Path(__file__).resolve().parents[2] / 'main/cpp/winlator/renderer/displayx.cpp').read_text()
callback = implementation[implementation.index('void DisplayX::onCompleteCallback('):implementation.index('void DisplayX::presentThreadLoop(')]
harness = r'''
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <cstring>
#include <deque>
#include <fcntl.h>
#include <memory>
#include <unordered_map>
#include <utility>
#include <vector>
#include <unistd.h>
struct Window {};
struct Drawable {};
struct ASurfaceTransactionStats {};
constexpr uint32_t VK_PRESENT_MODE_MAILBOX_KHR = 1;
constexpr uint32_t VK_PRESENT_MODE_FIFO_KHR = 2;
class DisplayX {
public:

'''
declarations = r'''
    struct OnCompleteContext { std::deque<std::unique_ptr<PresentRequest>> requests; };
    static void onCompleteCallback(void *, ASurfaceTransactionStats *);
};
using PresentRequest = DisplayX::PresentRequest;
using PresentQueue = DisplayX::PresentQueue;
'''
checks = r'''
int main() {
    Window a, b;
    PresentQueue queue;
    auto push = [&](Window *window, int id, bool mailbox, int fd = -1) {
        auto request = std::make_unique<PresentRequest>();
        request->window = window;
        request->sync_fence = fd;
        request->presentId = id;
        queue.push(std::move(request), mailbox);
    };
    int dropped = dup(STDIN_FILENO);
    assert(dropped >= 0);
    push(&a, 1, true, dropped);
    push(&a, 2, true);
    assert(fcntl(dropped, F_GETFD) == -1);
    auto latest = queue.getLast();
    assert(latest.size() == 1 && latest.front()->presentId == 2 && queue.empty());
    push(&a, 3, false);
    push(&a, 4, false);
    auto fifo = queue.buildWindowTree();
    assert(fifo.size() == 2 && fifo.front()->presentId == 3 && fifo.back()->presentId == 4 && queue.empty());
    int removed = dup(STDIN_FILENO);
    push(&a, 5, true, removed);
    push(&b, 6, true);
    queue.removeWindow(&a);
    assert(fcntl(removed, F_GETFD) == -1);
    auto remaining = queue.getLast();
    assert(remaining.size() == 1 && remaining.front()->window == &b && queue.empty());
    queue.removeWindow(nullptr);
    push(&a, 7, true);
    push(&b, 8, true);
    push(&a, 9, true);
    latest = queue.getLast();
    assert(latest.size() == 1 && latest.front()->presentId == 9);
    remaining = queue.getLast();
    assert(remaining.size() == 1 && remaining.front()->presentId == 8 && queue.empty());

    int fds[2];
    assert(pipe(fds) == 0);
    assert(fcntl(fds[0], F_SETFL, O_NONBLOCK) == 0);
    auto complete = [&](uint32_t mode, uint64_t id) {
        auto context = std::make_unique<DisplayX::OnCompleteContext>();
        auto request = std::make_unique<PresentRequest>();
        request->presentMode = mode;
        request->swapchainId = 7;
        request->index = 2;
        request->presentId = id;
        request->clientFd = fds[1];
        context->requests.push_back(std::move(request));
        DisplayX::onCompleteCallback(context.release(), nullptr);
        char reply[17];
        auto count = read(fds[0], reply, sizeof(reply));
        if (mode == VK_PRESENT_MODE_MAILBOX_KHR && id == UINT64_MAX) {
            assert(count == -1);
            return;
        }
        assert(count == sizeof(reply) && reply[4] == 7);
        uint64_t receivedId;
        uint32_t index;
        memcpy(&receivedId, reply + 5, 8);
        memcpy(&index, reply + 13, 4);
        assert(receivedId == id && index == 2);
    };
    complete(VK_PRESENT_MODE_MAILBOX_KHR, UINT64_MAX);
    complete(VK_PRESENT_MODE_FIFO_KHR, UINT64_MAX);
    complete(VK_PRESENT_MODE_MAILBOX_KHR, 42);
    close(fds[0]);
    close(fds[1]);
}
'''
with tempfile.TemporaryDirectory(prefix='displayx-queue-') as directory:
    temp = Path(directory)
    (temp / 'test.cpp').write_text(harness + request + queue + declarations + callback + checks)
    subprocess.run([os.environ.get('CXX', 'c++'), '-std=c++17', str(temp / 'test.cpp'), '-o', str(temp / 'test')], check=True)
    subprocess.run([str(temp / 'test')], check=True)
print('DisplayX mailbox, FIFO, window removal, fence, and completion protocol checks passed')
