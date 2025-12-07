import ui.SearchWindow;
import ctool.NativeMonitor;

public class Main {

    private static final String DB_PATH = "file_index.db";
    private static final String MONITOR_PATH = "D:\\";

    public static void main(String[] args) {

        // 启动 UI
        javax.swing.SwingUtilities.invokeLater(() ->
                new SearchWindow(DB_PATH));

        // 启动 C++ 索引构建器
        new Thread(Main::runIndexerOnce, "Indexer").start();

        // 启动 DLL 文件监控器
        new Thread(Main::startMonitor, "Monitor").start();
    }

    private static void runIndexerOnce() {
        try {
            ProcessBuilder pb = new ProcessBuilder("bin\\main.exe");
            pb.directory(new java.io.File("."));
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.err.println("[JAVA] 索引器启动失败: " + e.getMessage());
        }
    }

    private static void startMonitor() {
        try {
            int r = NativeMonitor.INSTANCE.StartFileMonitor(MONITOR_PATH, DB_PATH);
            System.out.println("[JAVA] 监控启动，返回码: " + r);
        } catch (Throwable e) {
            System.err.println("[JAVA] 启动监控失败: " + e);
        }
    }
}
