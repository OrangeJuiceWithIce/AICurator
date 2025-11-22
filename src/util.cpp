#include "../include/util.h"

std::string wide_to_utf8(const std::wstring& wstr) {
    if (wstr.empty()) return {};
    int size = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, nullptr, 0, nullptr, nullptr);
    std::string result(size - 1, 0);
    WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, result.data(), size - 1, nullptr, nullptr);
    return result;
}

std::wstring string_to_wstring(const std::string& str) {
    if (str.empty()) return {};
    int size = MultiByteToWideChar(CP_ACP, 0, str.c_str(), -1, nullptr, 0);
    std::wstring result(size - 1, 0);
    MultiByteToWideChar(CP_ACP, 0, str.c_str(), -1, result.data(), size - 1);
    return result;
}