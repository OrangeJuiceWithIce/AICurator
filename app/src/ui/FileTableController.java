package ui;

import db.SQLiteAccessor;
import model.AiAnalysis;
import model.FileRecord;
import service.AiAnalysisService;
import service.FileService;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FileTableController {

    private final SQLiteAccessor db;
    private final FileTableView view;
    private final FileService fileService;
    private final AiAnalysisService aiService;

    // --- 搜索相关：防抖 + 丢弃过期结果 ---
    private final Timer searchDebounce;
    private volatile String pendingKeyword = "";
    private final AtomicInteger searchSeq = new AtomicInteger(0); // 递增版本号

    public FileTableController(SQLiteAccessor db, FileTableView view) {
        this.db = db;
        this.view = view;
        this.fileService = new FileService();
        this.aiService = new AiAnalysisService(db);

        // 250ms 防抖：用户停止输入一会儿才真正触发查询
        this.searchDebounce = new Timer(250, e -> runSearchAsync(pendingKeyword));
        this.searchDebounce.setRepeats(false);

        wire();
    }

    private void wire() {
        view.setOnOpenPath(this::open);
        view.setOnDelete(this::delete);
        view.setOnRename(this::rename);
        view.setOnShowAi(this::showAi);
    }

    public void onQueryChanged(String keyword) {
        pendingKeyword = (keyword == null) ? "" : keyword;
        searchDebounce.restart(); // 频繁输入时只会触发最后一次
    }

    private void runSearchAsync(String keyword) {
        final int mySeq = searchSeq.incrementAndGet();

        new SwingWorker<List<FileRecord>, Void>() {
            @Override
            protected List<FileRecord> doInBackground() {
                // 注意：这里已经不在 EDT 了
                return db.search(keyword);
            }

            @Override
            protected void done() {
                // 丢弃旧查询结果：如果它不是最新那一次，就不更新 UI
                if (mySeq != searchSeq.get()) return;

                try {
                    List<FileRecord> data = get();
                    view.updateRows(data); // 回到 EDT 更新 UI（SwingWorker 的 done 在 EDT）
                } catch (Exception e) {
                    // 搜索失败不弹窗也行，避免打字时一直弹
                    // view.showInfo("搜索失败：" + e.getMessage());
                }
            }
        }.execute();
    }

    private void open(String path) {
        try {
            fileService.open(path);
        } catch (Exception e) {
            view.showInfo("无法打开文件:\n" + path);
        }
    }

    private void delete(String path) {
        if (!view.confirmDelete(path)) return;

        boolean ok = fileService.deleteReal(path);
        if (!ok) {
            view.showInfo("删除失败（可能被占用/无权限）:\n" + path);
            return;
        }

        db.delete(path);
        view.removeSelectedRow();
    }

    private void rename(String oldPath) {
        File oldFile = new File(oldPath);
        String newName = view.promptNewName(oldFile.getName());
        if (newName == null) return;

        String newPath = fileService.renameReal(oldPath, newName);
        if (newPath == null) {
            view.showInfo("重命名失败!");
            return;
        }

        db.rename(oldPath, newPath);
        view.updatePathForSelectedRow(newPath);
    }

    private void showAi(String path, boolean allowAnalyse) {
        AiAnalysis cached = aiService.getCached(path);
        if (cached != null && cached.origin != null && !cached.origin.isBlank()) {
            showAiDialog(path, cached, "AI 详情（缓存）");
            return;
        }

        if (!allowAnalyse) {
            view.showInfo("没有缓存结果。\n你可以选择“分析（写入缓存）”。");
            return;
        }

        final JDialog loading = view.showLoading("正在分析...", "正在调用 DeepSeek API，请稍候...");

        new SwingWorker<AiAnalysis, Void>() {
            @Override
            protected AiAnalysis doInBackground() {
                return aiService.analyseAndCache(path);
            }

            @Override
            protected void done() {
                loading.dispose();
                try {
                    AiAnalysis a = get();
                    if (a == null) {
                        view.showInfo("找不到该路径的索引记录:\n" + path);
                        return;
                    }
                    view.updateRiskForSelectedRow(a.risk);
                    showAiDialog(path, a, "AI 详情（新分析）");
                } catch (Exception e) {
                    view.showInfo("分析失败：" + e.getMessage());
                }
            }
        }.execute();
    }

    private void showAiDialog(String path, AiAnalysis a, String title) {
        JDialog dlg = new JDialog((java.awt.Frame) null, title, false);
        dlg.setLayout(new java.awt.BorderLayout(8, 8));

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 14));
        text.setLineWrap(true);
        text.setWrapStyleWord(true);

        String content =
                "Path:\n" + path + "\n\n" +
                "origin:\n" + a.origin + "\n\n" +
                "risk:\n" + a.risk + "\n\n" +
                "advice:\n" + a.advice + "\n\n" +
                "raw:\n" + a.raw;

        text.setText(content);

        JScrollPane sp = new JScrollPane(text);
        sp.setPreferredSize(new java.awt.Dimension(640, 420));

        JButton close = new JButton("关闭");
        close.addActionListener(e -> dlg.dispose());

        JPanel bottom = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        bottom.add(close);

        dlg.add(sp, java.awt.BorderLayout.CENTER);
        dlg.add(bottom, java.awt.BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }
}
