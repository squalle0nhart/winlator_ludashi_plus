#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
archive="$repo_root/app/src/main/assets/graphics_driver/extra_libs.tzst"
check_dir=$(mktemp -d "${TMPDIR:-/tmp}/displayx-check.XXXXXX")
trap 'rm -rf "$check_dir"' EXIT

tar --use-compress-program=unzstd -xf "$archive" -C "$check_dir" usr/lib/displayx_layer.so
llvm-nm "$check_dir/usr/lib/displayx_layer.so" > "$check_dir/symbols"

if grep -Eq 'networkListeningThread|init_network_thread_once' "$check_dir/symbols"; then
    echo "DisplayX layer still contains the unsafe global listener thread" >&2
    exit 1
fi

grep -q 'DisplayX_CreateSwapchainKHR' "$check_dir/symbols"
grep -q '_ZNSt6__ndk16thread4joinEv' "$check_dir/symbols"
grep -q ' shutdown$' "$check_dir/symbols"
