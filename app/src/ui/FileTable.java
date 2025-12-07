package ui;

import db.SQLiteAccessor;
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

    public FileTable(SQLiteAccessor db) {
        this.db = db;

        String[] cols = {"Path", "Size", "Created", "LastAccess", "LastWrite"};
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

    // --- 搜索监听器 ---
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

    // --- 更新数据 ---
    public void update(List<FileRecord> list) {
        model.setRowCount(0);
        for (var r : list) {
            model.addRow(new Object[]{
                    r.fullpath, r.size, r.creation, r.lastAccess, r.lastWrite
            });
        }
    }

    // ---------------------------------------------------------------
    // 双击打开文件
    // ---------------------------------------------------------------
    private void installDoubleClick() {
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row < 0) return;
                    String path = (String) model.getValueAt(row, 0);
                    openFile(path);
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // 右键菜单：删除 / 分析 / 重命名
    // ---------------------------------------------------------------
    private void installRightClickMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem("删除");
        JMenuItem analyseItem = new JMenuItem("分析文件用途");
        JMenuItem renameItem = new JMenuItem("重命名");

        menu.add(deleteItem);
        menu.add(analyseItem);
        menu.add(renameItem);

        deleteItem.addActionListener(e -> deleteSelected());
        analyseItem.addActionListener(e -> analyseSelected());
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

    // ---------------------------------------------------------------
    // 删除
    // ---------------------------------------------------------------
    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String path = (String) model.getValueAt(row, 0);

        int confirm = JOptionPane.showConfirmDialog(null,
                "确认删除?\n" + path, "删除确认", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        File f = new File(path);
        if (f.exists()) f.delete();

        db.delete(path);
        model.removeRow(row);
    }

    // ---------------------------------------------------------------
    // 使用 DeepSeek API 分析文件
    // ---------------------------------------------------------------
    private void analyseSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String path = (String) model.getValueAt(row, 0);
        FileRecord record = db.getByPath(path);

        // 显示非阻塞提示框
        final JDialog loading = new JDialog((Frame) null, "正在分析...", false);
        JLabel msg = new JLabel("正在调用 DeepSeek API，请稍候...");
        msg.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        loading.add(msg);
        loading.pack();
        loading.setLocationRelativeTo(null);
        loading.setVisible(true);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return DeepSeekClient.analyseFile(record);
            }

            @Override
            protected void done() {
                loading.dispose(); // 关闭“正在分析”窗口

                try {
                    String result = get();
                    JOptionPane.showMessageDialog(null, result,
                            "DeepSeek 文件分析", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                            "分析失败：" + e.getMessage());
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------
    // 重命名
    // ---------------------------------------------------------------
    private void renameSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String old = (String) model.getValueAt(row, 0);
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

        model.setValueAt(newPath, row, 0);
    }

    private void openFile(String path) {
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "无法打开文件:\n" + path);
        }
    }
}
