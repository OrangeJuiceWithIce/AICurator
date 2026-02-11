package ui;

import db.SQLiteAccessor;
import model.AiAnalysis;
import model.FileRecord;
import service.AiAnalysisService;
import service.FileService;
import util.OpLogger; // ✅ NEW

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FileTableController {

    private final SQLiteAccessor db;
    private final FileTableView view;
    private final FileService fileService;
    private final AiAnalysisService aiService;

    private final Timer searchDebounce;
    private volatile String pendingKeyword = "";
    private final AtomicInteger searchSeq = new AtomicInteger(0);

    public FileTableController(SQLiteAccessor db, FileTableView view) {
        this.db = db;
        this.view = view;
        this.fileService = new FileService();
        this.aiService = new AiAnalysisService(db);

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
        searchDebounce.restart();
    }

    private void runSearchAsync(String keyword) {
        final int mySeq = searchSeq.incrementAndGet();

        new SwingWorker<List<FileRecord>, Void>() {
            @Override
            protected List<FileRecord> doInBackground() {
                return db.search(keyword);
            }

            @Override
            protected void done() {
                if (mySeq != searchSeq.get()) return;

                try {
                    List<FileRecord> data = get();
                    view.updateRows(data);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void open(String path) {
        OpLogger.log("OPEN", path, "try");
        try {
            fileService.open(path);
            OpLogger.log("OPEN", path, "ok");
        } catch (Exception e) {
            OpLogger.log("OPEN", path, "fail: " + e.getMessage());
            view.showInfo("无法打开文件:\n" + path);
        }
    }

    private void delete(String path) {
        // 只记一条：最终结果
        if (!view.confirmDelete(path)) {
            util.OpLogger.log("DELETE", path, "cancel");
            return;
        }

        boolean ok = fileService.deleteReal(path);
        if (!ok) {
            util.OpLogger.log("DELETE", path, "fail: deleteReal=false");
            view.showInfo("删除失败（可能被占用/无权限）:\n" + path);
            return;
        }

        // 删除成功后再清 DB + UI
        try {
            db.delete(path);
            view.removeSelectedRow();
            util.OpLogger.log("DELETE", path, "ok");
        } catch (Exception e) {
            // 真实文件已删，但 DB/UI 同步失败，这也要写清楚
            util.OpLogger.log("DELETE", path, "partial_ok: file_deleted; db_or_ui_fail=" + e.getMessage());
            view.showInfo("文件已删除，但刷新/数据库同步失败:\n" + e.getMessage());
        }
    }

    private void rename(String oldPath) {
        File oldFile = new File(oldPath);
        String oldName = oldFile.getName();

        String newName = view.promptNewName(oldName);
        if (newName == null) {
            util.OpLogger.log("RENAME", oldPath, "cancel");
            return;
        }

        String newPath = fileService.renameReal(oldPath, newName);
        if (newPath == null) {
            util.OpLogger.log("RENAME", oldPath,
                    "fail: renameReal=null; oldName=" + oldName + "; newName=" + newName);
            view.showInfo("重命名失败!");
            return;
        }

        // DB + UI 同步
        try {
            db.rename(oldPath, newPath);
            view.updatePathForSelectedRow(newPath);

            File newFile = new File(newPath);
            String finalNewName = newFile.getName();

            // detail 写清楚：名字变更 + 路径变更
            util.OpLogger.log("RENAME", oldPath,
                    "ok: " + oldName + " -> " + finalNewName + " | " + oldPath + " -> " + newPath);
        } catch (Exception e) {
            // 文件已改名，但 DB/UI 失败
            util.OpLogger.log("RENAME", oldPath,
                    "partial_ok: file_renamed_to=" + newPath + "; db_or_ui_fail=" + e.getMessage());
            view.showInfo("文件已重命名，但刷新/数据库同步失败:\n" + e.getMessage());
        }
    }

    private void showAi(String path, boolean allowAnalyse) {
        // allowAnalyse=false 表示“查看 AI 字段（不分析）”
        OpLogger.log("AI_VIEW", path, allowAnalyse ? "allowAnalyse=1" : "allowAnalyse=0");

        AiAnalysis cached = aiService.getCached(path);
        if (cached != null && cached.origin != null && !cached.origin.isBlank()) {
            OpLogger.log("AI_VIEW_CACHE", path, "hit");
            showAiDialog(path, cached, "AI 详情（缓存）");
            return;
        }

        if (!allowAnalyse) {
            OpLogger.log("AI_VIEW_CACHE", path, "miss (no_analyse)");
            view.showInfo("没有缓存结果。\n你可以选择“分析（写入缓存）”。");
            return;
        }

        OpLogger.log("AI_ANALYZE", path, "start");
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
                        OpLogger.log("AI_ANALYZE", path, "fail: no_index_record");
                        view.showInfo("找不到该路径的索引记录:\n" + path);
                        return;
                    }
                    view.updateRiskForSelectedRow(a.risk);
                    OpLogger.log("AI_ANALYZE", path, "ok: risk=" + a.risk);
                    showAiDialog(path, a, "AI 详情（新分析）");
                } catch (Exception e) {
                    OpLogger.log("AI_ANALYZE", path, "fail: " + e.getMessage());
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
