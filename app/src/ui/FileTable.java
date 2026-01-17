package ui;

import db.SQLiteAccessor;
import model.AiAnalysis;
import model.FileRecord;
import util.DeepSeekClient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.File;
import java.util.List;

public class FileTable {

    private final SQLiteAccessor db;
    private final JTable table;
    private final DefaultTableModel model;

    // Risk 列下标（固定最后一列更简单）
    private static final int COL_PATH = 0;
    private static final int COL_RISK = 5;

    public FileTable(SQLiteAccessor db) {
        this.db = db;

        String[] cols = {"Path", "Size", "Created", "LastAccess", "LastWrite", "Risk"};
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        table.setRowHeight(22);

        installDoubleClick();
        installRightClickMenu();
    }

    public JTable getTable() {
        return table;
    }

    public DocumentListener createSearchListener(JTextField tf) {
        return new DocumentListener() {
            private void refresh() {
                List<FileRecord> data = db.search(tf.getText());
                FileTable.this.update(data);
            }
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        };
    }

    public void update(List<FileRecord> list) {
        model.setRowCount(0);
        for (var r : list) {
            String riskText = (r.aiRisk < 0) ? "" : String.valueOf(r.aiRisk);
            model.addRow(new Object[]{
                    r.fullpath, r.size, r.creation, r.lastAccess, r.lastWrite, riskText
            });
        }
    }

    /**
     * 双击行为：
     * - 双击 Risk 列：弹出窗口显示所有 AI 字段（origin/risk/advice/raw）
     * - 双击其他列：打开文件
     */
    private void installDoubleClick() {
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;

                int row = table.getSelectedRow();
                int col = table.getSelectedColumn();
                if (row < 0) return;

                String path = (String) model.getValueAt(row, COL_PATH);

                if (col == COL_RISK) {
                    showAiDetailWindow(path, true); // true=允许触发 AI（缓存没命中才调用）
                } else {
                    openFile(path);
                }
            }
        });
    }

    private void installRightClickMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem("删除");
        JMenuItem analyseItem = new JMenuItem("分析（写入缓存）");
        JMenuItem showTagItem = new JMenuItem("查看 AI 字段（不分析）");
        JMenuItem renameItem = new JMenuItem("重命名");

        menu.add(deleteItem);
        menu.add(analyseItem);
        menu.add(showTagItem);
        menu.add(renameItem);

        deleteItem.addActionListener(e -> deleteSelected());
        analyseItem.addActionListener(e -> analyseSelected());
        showTagItem.addActionListener(e -> showTagSelected());
        renameItem.addActionListener(e -> renameSelected());

        table.addMouseListener(new MouseAdapter() {
            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    menu.show(table, e.getX(), e.getY());
                }
            }
            public void mousePressed(MouseEvent e) { showPopup(e); }
            public void mouseReleased(MouseEvent e) { showPopup(e); }
        });
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String path = (String) model.getValueAt(row, COL_PATH);

        int confirm = JOptionPane.showConfirmDialog(null,
                "确认删除?\n" + path, "删除确认", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        File f = new File(path);
        if (f.exists()) f.delete();

        db.delete(path);
        model.removeRow(row);
    }

    /**
     * 右键“分析”：强制走一次 API，然后写缓存
     */
    private void analyseSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String path = (String) model.getValueAt(row, COL_PATH);
        showAiDetailWindow(path, true);
    }

    /**
     * 右键“查看”：只看缓存，不触发 API
     */
    private void showTagSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String path = (String) model.getValueAt(row, COL_PATH);
        showAiDetailWindow(path, false);
    }

    /**
     * 核心：展示窗口
     * allowAnalyse=true 时，缓存不存在才调用 API
     */
    private void showAiDetailWindow(String path, boolean allowAnalyse) {
        // 1) 先读缓存
        AiAnalysis cached = db.getAiAnalysis(path);
        if (cached != null && !cached.origin.isBlank()) {
            showAiDialog(path, cached, "AI 详情（缓存）");
            return;
        }

        if (!allowAnalyse) {
            JOptionPane.showMessageDialog(null, "没有缓存结果。\n你可以选择“分析（写入缓存）”。");
            return;
        }

        // 2) 没缓存 -> 调 API -> 解析 -> 写缓存 -> 显示
        FileRecord record = db.getByPath(path);
        if (record == null) {
            JOptionPane.showMessageDialog(null, "找不到该路径的索引记录:\n" + path);
            return;
        }

        final JDialog loading = new JDialog((Frame) null, "正在分析...", false);
        JLabel msg = new JLabel("正在调用 DeepSeek API，请稍候...");
        msg.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        loading.add(msg);
        loading.pack();
        loading.setLocationRelativeTo(null);
        loading.setVisible(true);

        new SwingWorker<AiAnalysis, Void>() {
            @Override
            protected AiAnalysis doInBackground() {
                return DeepSeekClient.analyseFileStructured(record);
            }

            @Override
            protected void done() {
                loading.dispose();
                try {
                    AiAnalysis a = get();

                    // 写缓存（按字段写入）
                    db.upsertAiAnalysis(path, a);

                    // 更新表格 risk 列（只更新当前行）
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        model.setValueAt(String.valueOf(a.risk), row, COL_RISK);
                    }

                    showAiDialog(path, a, "AI 详情（新分析）");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "分析失败：" + e.getMessage());
                }
            }
        }.execute();
    }

    /**
     * 小窗口：展示所有字段
     */
    private void showAiDialog(String path, AiAnalysis a, String title) {
        JDialog dlg = new JDialog((Frame) null, title, false);
        dlg.setLayout(new BorderLayout(8, 8));

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
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
        sp.setPreferredSize(new Dimension(640, 420));

        JButton close = new JButton("关闭");
        close.addActionListener(e -> dlg.dispose());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);

        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    private void renameSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String old = (String) model.getValueAt(row, COL_PATH);
        File oldFile = new File(old);

        String newName = JOptionPane.showInputDialog("输入新文件名:", oldFile.getName());
        if (newName == null || newName.isBlank()) return;

        File newFile = new File(oldFile.getParent(), newName);
        if (!oldFile.renameTo(newFile)) {
            JOptionPane.showMessageDialog(null, "重命名失败!");
            return;
        }

        String newPath = newFile.getAbsolutePath();

        db.rename(old, newPath);
        model.setValueAt(newPath, row, COL_PATH);
    }

    private void openFile(String path) {
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "无法打开文件:\n" + path);
        }
    }
}
