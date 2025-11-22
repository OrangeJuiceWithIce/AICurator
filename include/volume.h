#pragma once
#include <windows.h>
#include <string>
#include <unordered_map>

struct PfrnName{
    DWORDLONG pfrn=0;
    std::wstring filename;
};

class Volume{
private:
    HANDLE hVol;//目标卷的句柄
    char volLetter;//卷的驱动器字母,如C\D
    CREATE_USN_JOURNAL_DATA cujd;//创建USN日志的参数
    USN_JOURNAL_DATA ujd;//查询USN日志得到的结果，例如最小usn等等

public:
    std::unordered_map<DWORDLONG,PfrnName> frnMap;
    Volume(char vol):hVol(INVALID_HANDLE_VALUE),volLetter(vol){}

    bool getHandle();
    bool createUSN();
    bool getUSNInfo();
    bool getUSNJournal();
    bool deleteUSN();
    void getPath(DWORDLONG frn, std::wstring& path);
    void closeHandle();
};

