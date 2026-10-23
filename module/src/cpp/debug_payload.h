#pragma once
#ifdef FP_DEBUG_FILE_DEX
#include <vector>
enum class DebugPayloadResult { Missing, Loaded, Invalid };
DebugPayloadResult loadDebugPayload(const char* appData, std::vector<unsigned char>& payload);
#endif
