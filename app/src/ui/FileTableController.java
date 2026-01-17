package ui;

import db.SQLiteAccessor;
import model.AiAnalysis;
import model.FileRecord;
import service.AiAnalysisService;
import service.FileService;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class FileTableController {

    private final SQLiteAccessor db;
    private final FileTableView view;
    private final FileService fileService;
    private final AiAnalysisService aiService;

    public FileTableController(SQLiteAccessor db, FileTableView view) {
        this.db = db;
        this.view = view;
        this.fileService = new FileService();
        this.aiService = new AiAnalysisService(db);

        wire();
    }

    private void wire() {
        view.setOnOpenPath(this::open);
        view.setOnDelete(this::delete);
        view.setOnRename(this::rename);
        view.setOnShowAi(this::showAi);
    }

    public void onQueryChanged(String keyword) {
        List<FileRecord> data = db.search(keyword);
        view.updateRows(data);
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

        // 你现在 DLL monitor 也会同步删 DB；Java 这里删 DB 属于“即时刷新”
        // 如果你想彻底避免双写，可以注释掉下一行，只靠 monitor 更新
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

        // 同上：可选是否由 Java 立即改 DB
        db.rename(oldPath, newPath);

        view.updatePathForSelectedRow(newPath);
    }

    private void showAi(String path, boolean allowAnalyse) {
        // 1) 缓存命中直接显示
        AiAnalysis cached = aiService.getCached(path);
        if (cached != null && cached.origin != null && !cached.origin.isBlank()) {
            showAiDialog(path, cached, "AI 详情（缓存）");
            return;
        }

        if (!allowAnalyse) {
            view.showInfo("没有缓存结果。\n你可以选择“分析（写入缓存）”。");
            return;
        }

        // 2) 异步分析 + 写缓存
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
        // 这里依然属于 UI 展示，你也可以继续下沉到 view 里
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