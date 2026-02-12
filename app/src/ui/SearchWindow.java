package ui;

import db.SQLiteAccessor;

import javax.swing.*;
import java.awt.*;

public class SearchWindow extends JFrame {

    private SQLiteAccessor db;
    private FileTableView view;
    private FileTableController controller;

    private JTextField searchField;
    private LoadingGlassPane loading;

    private volatile boolean dbReady = false;

    public SearchWindow(String dbPath) {
        super("AICurator");
        ModernTheme.install();

        buildSkeletonUI();
        setSize(1050, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        // 启动时直接锁 UI
        setBusy(true, "正在启动", "准备数据库与索引，请稍候...");

        // 1) 先后台初始化 DB + Controller（但不解除遮罩）
        initDbAsync(dbPath);

        // 2) 同时等待 indexer（exe）完成（也不解除遮罩）
        waitIndexerAsync();
    }

    private void buildSkeletonUI() {
        view = new FileTableView();

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(Color.WHITE);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setOpaque(false);

        JLabel title = new JLabel("AICurator");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        // ✅ right tools
        JButton logBtn = new JButton("日志");
        logBtn.addActionListener(e -> new LogWindow());
        logBtn.setFocusPainted(false);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(logBtn);

        JButton ossBtn = new JButton("OSS设置");
        ossBtn.addActionListener(e -> OssSettingsDialog.show(this, false));
        ossBtn.setFocusPainted(false);
        right.add(ossBtn);


        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search path...");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        searchField.setEnabled(false);

        top.add(title, BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        JScrollPane sp = new JScrollPane(view.getTable());
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)));
        sp.getViewport().setBackground(Color.WHITE);

        root.add(top, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);

        setContentPane(root);

        loading = new LoadingGlassPane();
        setGlassPane(loading);
    }

    private void initDbAsync(String dbPath) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                db = new SQLiteAccessor(dbPath);
                controller = new FileTableController(db, view);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    dbReady = true;

                    // DB ready 了，但如果 indexer 还在跑，遮罩不能消失，只更新文案
                    loading.updateText("正在构建索引", "数据库已就绪，正在等待索引器完成检索与写入...");

                    // 注意：搜索监听先不挂，避免用户在遮罩消失瞬间抖动触发
                    // 等到真正 readyToInteract() 的时候再统一开启
                    tryEnableInteractionsIfReady();

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SearchWindow.this,
                            "数据库初始化失败: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void waitIndexerAsync() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 如果 Main 里没启动 indexer，这里也会一直等（但你的 Main 确实会启动）
                StartupGate.awaitIndexDone();
                return null;
            }

            @Override
            protected void done() {
                // indexer done 了，但 DB 可能还没 ready，同样不能直接放开
                int exit = StartupGate.getIndexExitCode();
                if (exit != 0) {
                    // 这里不强制弹窗（避免吵），只改文案；你也可以改成 JOptionPane
                    loading.updateText("索引器已结束", "索引器退出码: " + exit + "（可能索引不完整）");
                } else {
                    loading.updateText("索引写入完成", "正在加载最新数据...");
                }

                tryEnableInteractionsIfReady();
            }
        }.execute();
    }

    private void tryEnableInteractionsIfReady() {
        // 必须两者都完成：DB ready + indexer done
        if (!dbReady) return;
        if (!StartupGate.isIndexDone()) return;

        // 现在才是真正可以交互的时机
        SwingUtilities.invokeLater(() -> {
            // 绑定搜索监听
            searchField.getDocument().addDocumentListener(
                    view.createSearchListener(controller::onQueryChanged, searchField)
            );

            // 解除遮罩 & 开启交互
            setBusy(false, null, null);

            // 刷新一次，确保拿到 indexer 写入后的最新数据
            controller.onQueryChanged(searchField.getText());
        });
    }

    public void setBusy(boolean busy, String title, String message) {
        if (busy) {
            view.setInteractionsEnabled(false);
            searchField.setEnabled(false);
            loading.showLoading(title, message);
        } else {
            loading.hideLoading();
            view.setInteractionsEnabled(true);
            searchField.setEnabled(true);
        }
    }
}
