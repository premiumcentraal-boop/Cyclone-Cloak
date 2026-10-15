#pragma once

#include <string_view>

// The publisher/diagnostics app must retain the real framework state in every Android user.
constexpr bool runtime_package_allowed(std::string_view package_name) {
    return package_name != "dev.cyclone.cloak";
}

static_assert(!runtime_package_allowed("dev.cyclone.cloak"));
static_assert(runtime_package_allowed("dev.cyclone.cloak.probe"));
static_assert(runtime_package_allowed("com.android.chrome"));
