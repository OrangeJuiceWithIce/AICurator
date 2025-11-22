import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

/**
 * 简易版 Everything 风格 UI + JNA 调用。
 * 后台启动 main.exe 构建索引 + 调用 DLL 监听文件变化。
 */
public class Main {

    private static final String DB_PATH = "file_index.db";
    private static final String MONITOR_PATH = "D:\\";

    public static void main(String[] args) {
        // 先启动 UI，避免阻塞
        SwingUtilities.invokeLater(Main::createAndShowUI);

        // 后台执行索引 & 启动监控
        new Thread(() -> {
            runIndexerOnce();    // 构建索引
            startNativeMonitor(); // 启动目录监控
        }, "Monitor-Thread").start();
    }

    /**
     * 启动时调用一次 C++ 的 main.exe，重建索引。
     * 直接继承控制台 IO，避免编码问题和额外线程。
     */
    private static void runIndexerOnce() {
        try {
            System.out.println("[JAVA] 启动索引器 main.exe ...");
            ProcessBuilder pb = new ProcessBuilder("bin\\main.exe");
            pb.directory(new java.io.File(".")); // 工作目录 = simple 根目录
            // 让 main.exe 的输出直接写到当前控制台，避免 UTF-16 / UTF-8 乱码
            pb.inheritIO();

            Process p = pb.start();
            int code = p.waitFor();
            System.out.println("[JAVA] main.exe 退出码 = " + code);
        } catch (Exception e) {
            System.err.println("[JAVA] 调用 main.exe 失败: " + e.getMessage());
        }
    }

    /**
     * 启动 DLL 文件监控。
     */
    private static void startNativeMonitor() {
        try {
            System.out.println("[JAVA] 调用 DLL StartFileMonitor ...");
            int ret = NativeMonitor.INSTANCE.StartFileMonitor(MONITOR_PATH, DB_PATH);
            System.out.println("[JAVA] StartFileMonitor 返回码: " + ret);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[JAVA] 加载 DLL 失败，请确认文件在路径中: " + e.getMessage());
        } catch (Throwable t) {
            System.err.println("[JAVA] 启动监控失败: " + t);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                NativeMonitor.INSTANCE.StopFileMonitor();
                System.out.println("[JAVA] StopFileMonitor 已调用");
            } catch (Throwable ignored) {
            }
        }));
    }

    /**
     * Swing UI
     */
    private static void createAndShowUI() {
        JFrame frame = new JFrame("Simple Everything");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField searchField = new JTextField();
        // 使用支持中文的字体（如微软雅黑），避免中文显示为方块或乱码
        searchField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));

        String[] cols = {"Path"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        table.setRowHeight(22);
        JScrollPane scrollPane = new JScrollPane(table);

        frame.setLayout(new BorderLayout(4, 4));
        frame.add(searchField, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 动态查询
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            void onChange() {
                String keyword = searchField.getText();
                loadData(keyword, model);
            }
            public void insertUpdate(DocumentEvent e) { onChange(); }
            public void removeUpdate(DocumentEvent e) { onChange(); }
            public void changedUpdate(DocumentEvent e) { onChange(); }
        });

        loadData("", model);
    }

    /**
     * 从 SQLite 查询路径
     */
    private static void loadData(String prefix, DefaultTableModel model) {
        model.setRowCount(0);
        String url = "jdbc:sqlite:" + DB_PATH;

        String sql = "SELECT path FROM files ";
        if (prefix != null && !prefix.isEmpty()) sql += "WHERE path LIKE ? ";
        sql += "ORDER BY id LIMIT 1000";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (prefix != null && !prefix.isEmpty())
                ps.setString(1, "%" + prefix + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    model.addRow(new Object[]{rs.getString("path")});
            }
        } catch (SQLException e) {
            System.err.println("[JAVA] 查询数据库失败: " + e.getMessage());
        }
    }
}
