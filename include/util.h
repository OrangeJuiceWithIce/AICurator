#pragma once
#include <string>
#include <windows.h>

// 字符编码转换
std::string wide_to_utf8(const std::wstring& wstr);
std::wstring string_to_wstring(const std::string& str);