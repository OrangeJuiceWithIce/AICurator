import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

/**
 * 使用 JNA 调用 C++ DLL 中的 StartFileMonitor / StopFileMonitor。
 * 对应 monitor_api.h 中的 __stdcall 函数。
 */
public interface NativeMonitor extends StdCallLibrary {

    // 加载名为 file_monitor 的 DLL（即 file_monitor.dll）
    NativeMonitor INSTANCE = Native.load("bin\\file_monitor", NativeMonitor.class);

    // 对应: int __stdcall StartFileMonitor(const char* monitorPath, const char* dbPath);
    int StartFileMonitor(String monitorPath, String dbPath);

    // 对应: void __stdcall StopFileMonitor();
    void StopFileMonitor();
}