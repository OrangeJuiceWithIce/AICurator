#include "../include/monitor.h"
#include "../include/database.h"
#include "../include/util.h"
#include <iostream>
#include <cstring>

DirectoryMonitor::DirectoryMonitor(const std::string& path, Database* database)
    : dirHandle(INVALID_HANDLE_VALUE), monitorPath(path), db(database) {}

DirectoryMonitor::~DirectoryMonitor() {
    stop();
    if (dirHandle != INVALID_HANDLE_VALUE) {
        CloseHandle(dirHandle);
    }
}

void DirectoryMonitor::start() {
    std::wstring wpath = string_to_wstring(monitorPath);

    dirHandle = CreateFileW(
        wpath.c_str(),
        FILE_LIST_DIRECTORY,
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
        nullptr,
        OPEN_EXISTING,
        FILE_FLAG_BACKUP_SEMANTICS,
        nullptr
    );

    if (dirHandle == INVALID_HANDLE_VALUE) {
        std::cerr << "[ERROR] 无法打开目录: " << monitorPath
                  << " 错误码: " << GetLastError() << std::endl;
        return;
    }

    std::cout << "[INFO] 开始监控目录: " << monitorPath << std::endl;
    isRunning = true;

    constexpr DWORD BUFFER_SIZE = 16 * 1024;
    BYTE notifyBuffer[BUFFER_SIZE];
    DWORD bytesReturned;

    std::string lastOldPath;

    while (isRunning) {
        memset(notifyBuffer, 0, BUFFER_SIZE);

        BOOL ok = ReadDirectoryChangesW(
            dirHandle,
            notifyBuffer,
            BUFFER_SIZE,
            TRUE,
            FILE_NOTIFY_CHANGE_FILE_NAME |
            FILE_NOTIFY_CHANGE_DIR_NAME |
            FILE_NOTIFY_CHANGE_SIZE,
            &bytesReturned,
            nullptr,
            nullptr
        );

        if (!ok) {
            DWORD err = GetLastError();

            if (err != ERROR_IO_PENDING) {
                std::cerr << "[WARN] ReadDirectoryChangesW 失败: "
                          << err << "，继续监控..." << std::endl;
            }
            continue;// 继续运行
        }

        BYTE* base = notifyBuffer;

        for (DWORD offset = 0; offset < bytesReturned;) {

            auto* pNotify =
                reinterpret_cast<PFILE_NOTIFY_INFORMATION>(base + offset);

            std::wstring wname(pNotify->FileName,
                               pNotify->FileNameLength / sizeof(WCHAR));
            std::string fileNameA = wide_to_utf8(wname);

            // 拼接完整路径
            std::string fullPath = monitorPath;
            if (!fullPath.empty() &&
                fullPath.back() != '\\' &&
                fullPath.back() != '/') {
                fullPath += "\\";
            }
            fullPath += fileNameA;

            // 过滤掉一些文件，防止无意义的事件被捕获
            if (fullPath.find("$RECYCLE.BIN") == std::string::npos &&
                fullPath.find("file_index.db-journal") == std::string::npos) {
                switch (pNotify->Action) {

                case FILE_ACTION_ADDED:
                    std::cout << "[MONITOR] 文件添加: " << fullPath << std::endl;
                    if (db && db->isConnected()) db->addRecord(makeRecord(fullPath));
                    break;

                case FILE_ACTION_MODIFIED:
                    std::cout << "[MONITOR] 文件修改: " << fullPath << std::endl;
                    if (db && db->isConnected()) {
                        if (!db->recordExists(fullPath))
                            db->addRecord(makeRecord(fullPath));
                    }
                    break;

                case FILE_ACTION_REMOVED:
                    std::cout << "[MONITOR] 文件删除: " << fullPath << std::endl;
                    if (db && db->isConnected()) db->deleteRecord(fullPath);
                    break;

                case FILE_ACTION_RENAMED_OLD_NAME:
                    lastOldPath = fullPath;
                    break;

                case FILE_ACTION_RENAMED_NEW_NAME:
                    if (!lastOldPath.empty()) {
                        std::cout << "[MONITOR] 文件重命名: "
                                << lastOldPath << " -> " << fullPath << std::endl;

                        if (db && db->isConnected()) {
                            db->updatePathsOnDirectoryRename(lastOldPath, fullPath);
                            db->deleteRecord(lastOldPath);
                            db->addRecord(makeRecord(fullPath));
                        }

                        lastOldPath.clear();
                    }
                    break;


                default:
                    std::cout << "[MONITOR] 未知操作: "
                            << pNotify->Action << std::endl;
                    break;
                }
            }
            
            if (pNotify->NextEntryOffset == 0)
                break;

            offset += pNotify->NextEntryOffset;
        }
    }

    CloseHandle(dirHandle);
    dirHandle = INVALID_HANDLE_VALUE;

    std::cout << "[INFO] 停止监控目录: " << monitorPath << std::endl;
}
