#!/bin/sh
set -eu

glslc_bin="$1"
stage="$2"
symbol="$3"
input="$4"
output="$5"
tmp_spv="$(mktemp "${TMPDIR:-/tmp}/winlator-shader.XXXXXX")"
trap 'rm -f "$tmp_spv"' EXIT

"$glslc_bin" -fshader-stage="$stage" -c "$input" -o "$tmp_spv"

if [ ! -s "$tmp_spv" ]; then
    echo "Shader compilation produced no output: $input" >&2
    exit 1
fi

{
    printf '#include <stdint.h>\n'
    printf 'static const uint32_t %s[] = {\n' "$symbol"
    od -An -v -t u4 "$tmp_spv" | while read -r line; do
        for word in $line; do
            printf '    %su,\n' "$word"
        done
    done
    printf '\n};\n'
} > "$output"
