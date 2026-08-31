if(NOT DEFINED SOURCE_FILE OR NOT EXISTS "${SOURCE_FILE}")
    message(FATAL_ERROR "Shader source not found: ${SOURCE_FILE}")
endif()
if(NOT DEFINED OUTPUT_FILE OR NOT DEFINED VARIABLE_NAME)
    message(FATAL_ERROR "OUTPUT_FILE and VARIABLE_NAME are required")
endif()

get_filename_component(output_dir "${OUTPUT_FILE}" DIRECTORY)
file(MAKE_DIRECTORY "${output_dir}")

if(DEFINED GLSLANG_VALIDATOR AND EXISTS "${GLSLANG_VALIDATOR}")
    execute_process(
        COMMAND "${GLSLANG_VALIDATOR}" -V --vn "${VARIABLE_NAME}" -x
                -o "${OUTPUT_FILE}" "${SOURCE_FILE}"
        RESULT_VARIABLE result
        ERROR_VARIABLE error
    )
    if(NOT result EQUAL 0)
        message(FATAL_ERROR "glslangValidator failed for ${SOURCE_FILE}: ${error}")
    endif()
    return()
endif()

if(NOT DEFINED GLSL_COMPILER OR NOT EXISTS "${GLSL_COMPILER}")
    message(FATAL_ERROR "No GLSL compiler found")
endif()

set(spirv_file "${OUTPUT_FILE}.spv")
execute_process(
    COMMAND "${GLSL_COMPILER}" "${SOURCE_FILE}" -o "${spirv_file}"
    RESULT_VARIABLE result
    ERROR_VARIABLE error
)
if(NOT result EQUAL 0)
    file(REMOVE "${spirv_file}")
    message(FATAL_ERROR "glslc failed for ${SOURCE_FILE}: ${error}")
endif()

file(READ "${spirv_file}" spirv_hex HEX)
file(REMOVE "${spirv_file}")
string(LENGTH "${spirv_hex}" hex_length)
math(EXPR remainder "${hex_length} % 8")
if(NOT remainder EQUAL 0)
    message(FATAL_ERROR "Invalid SPIR-V byte length for ${SOURCE_FILE}")
endif()

set(header "#include <stdint.h>\nstatic const uint32_t ${VARIABLE_NAME}[] = {\n")
math(EXPR last_word "${hex_length} / 8 - 1")
foreach(index RANGE 0 ${last_word})
    math(EXPR offset "${index} * 8")
    string(SUBSTRING "${spirv_hex}" ${offset} 2 byte0)
    math(EXPR offset "${offset} + 2")
    string(SUBSTRING "${spirv_hex}" ${offset} 2 byte1)
    math(EXPR offset "${offset} + 2")
    string(SUBSTRING "${spirv_hex}" ${offset} 2 byte2)
    math(EXPR offset "${offset} + 2")
    string(SUBSTRING "${spirv_hex}" ${offset} 2 byte3)
    math(EXPR word "0x${byte3}${byte2}${byte1}${byte0}")
    string(APPEND header "    ${word}u,\n")
endforeach()
string(APPEND header "};\n")
file(WRITE "${OUTPUT_FILE}" "${header}")
