#include "../include/volume.h"
#include <fcntl.h>
#include <io.h>
#include <iostream>
#include "../include/util.h"   // 需要 wide_to_utf8()

bool Volume::getHandle() {
    std::wstring path = L"\\\\.\\C:";        // 打开卷 C:
    path[4] = static_cast<wchar_t>(volLetter);

    hVol = CreateFileW(
        path.c_str(),
        GENERIC_READ | GENERIC_WRITE,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        nullptr,
        OPEN_EXISTING,
        FILE_ATTRIBUTE_READONLY,
        nullptr
    );

    if (hVol != INVALID_HANDLE_VALUE) {
        std::cout << "[INFO] 成功打开卷: " << wide_to_utf8(path) << std::endl;
        return true;
    }

    DWORD errCode = GetLastError();
    std::cerr << "[ERROR] 打开卷失败, 错误码: " << errCode << std::endl;
    return false;
}

// 创建 USN 日志
bool Volume::createUSN() {
    cujd.MaximumSize = 0;
    cujd.AllocationDelta = 0;
    DWORD br = 0;

    BOOL success = DeviceIoControl(
        hVol,
        FSCTL_CREATE_USN_JOURNAL,
        &cujd,
        sizeof(cujd),
        nullptr,
        0,
        &br,
        nullptr
    );

    if (success) {
        std::cout << "[INFO] 成功创建 USN 日志。" << std::endl;
        return true;
    } else {
        std::cerr << "[ERROR] 创建 USN 日志失败，错误码: "
                  << GetLastError() << std::endl;
        return false;
    }
}

// 获取 USN 信息
bool Volume::getUSNInfo() {
    DWORD br = 0;
    BOOL success = DeviceIoControl(
        hVol,
        FSCTL_QUERY_USN_JOURNAL,
        nullptr,
        0,
        &ujd,
        sizeof(ujd),
        &br,
        nullptr
    );

    if (success) {
        std::cout << "[INFO] 获取 USN 日志信息成功。" << std::endl;
        return true;
    } else {
        std::cerr << "[ERROR] 获取 USN 信息失败，错误码: "
                  << GetLastError() << std::endl;
        return false;
    }
}

// 删除 USN
bool Volume::deleteUSN() {
    DELETE_USN_JOURNAL_DATA dujd;
    dujd.UsnJournalID = ujd.UsnJournalID;
    dujd.DeleteFlags = USN_DELETE_FLAG_DELETE;
    DWORD br;

    if (DeviceIoControl(hVol, FSCTL_DELETE_USN_JOURNAL,
        &dujd, sizeof(dujd), nullptr, 0, &br, nullptr)) {
        std::cout << "[INFO] 删除 USN 日志信息成功。" << std::endl;
        return true;
    }

    std::cerr << "[ERROR] 删除 USN 信息失败，错误码: "
              << GetLastError() << std::endl;
    return false;
}

// 读取 USN 日志
bool Volume::getUSNJournal() {
    MFT_ENUM_DATA med{};
    med.StartFileReferenceNumber = 0;
    med.LowUsn = 0;
    med.HighUsn = ujd.NextUsn;

    constexpr DWORD BUF_LEN = 0x3900;
    char buffer[BUF_LEN];
    DWORD bytesReturned;

    while (DeviceIoControl(
        hVol,
        FSCTL_ENUM_USN_DATA,
        &med,
        sizeof(med),
        buffer,
        BUF_LEN,
        &bytesReturned,
        nullptr)
    ) {
        DWORD dwRetBytes = bytesReturned - sizeof(USN);
        auto usnRecord = reinterpret_cast<PUSN_RECORD>(buffer + sizeof(USN));

        while (dwRetBytes > 0) {
            std::wstring fileName(usnRecord->FileName,
                                  usnRecord->FileNameLength / sizeof(WCHAR));
            PfrnName node;
            node.filename = fileName;
            node.pfrn = usnRecord->ParentFileReferenceNumber;
            
            frnMap[usnRecord->FileReferenceNumber] = node;

            dwRetBytes -= usnRecord->RecordLength;
            usnRecord = reinterpret_cast<PUSN_RECORD>(
                reinterpret_cast<BYTE*>(usnRecord) + usnRecord->RecordLength);
        }
        med.StartFileReferenceNumber = *reinterpret_cast<USN*>(buffer);
    }
    std::cout << "[INFO] USN 日志读取完毕。" << std::endl;
    return true;
}

void Volume::getPath(DWORDLONG frn,std::wstring& path){
    //思路就是从空路径开始,先查找出当前frn的文件名，然后再对其父目录重复操作
    path.clear();
    auto it=frnMap.find(frn);

    while(it!=frnMap.end()){
        path=L"\\"+it->second.filename+path;
        frn=it->second.pfrn;
        it=frnMap.find(frn);
    }

    path=std::wstring(1,static_cast<wchar_t>(volLetter))+L":"+path;
}

// 关闭句柄
void Volume::closeHandle() {
    if (hVol != INVALID_HANDLE_VALUE) {
        CloseHandle(hVol);
        hVol = INVALID_HANDLE_VALUE;
    }
}