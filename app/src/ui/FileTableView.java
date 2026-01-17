package ui;

import model.FileRecord;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileTableView {
    public static final int COL_PATH = 0;
    public static final int COL_RISK = 5;

    private final JTable table;
    private final DefaultTableModel model;

    private Consumer<String> onOpenPath = p -> {};
    private BiConsumer<String, Boolean> onShowAi = (p, allow) -> {};
    private Consumer<String> onDelete = p -> {};
    private Consumer<String> onRename = p -> {};

    public FileTableView() {
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

    public JTable getTable() { return table; }

    public void setOnOpenPath(Consumer<String> cb) { this.onOpenPath = cb; }
    public void setOnShowAi(BiConsumer<String, Boolean> cb) { this.onShowAi = cb; }
    public void setOnDelete(Consumer<String> cb) { this.onDelete = cb; }
    public void setOnRename(Consumer<String> cb) { this.onRename = cb; }

    public DocumentListener createSearchListener(Consumer<String> onQueryChanged, JTextField tf) {
        return new DocumentListener() {
            private void refresh() { onQueryChanged.accept(tf.getText()); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        };
    }

    public String getSelectedPath() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        return (String) model.getValueAt(row, COL_PATH);
    }

    public void updateRows(List<FileRecord> list) {
        model.setRowCount(0);
        for (var r : list) {
            String riskText = (r.aiRisk < 0) ? "" : String.valueOf(r.aiRisk);
            model.addRow(new Object[]{r.fullpath, r.size, r.creation, r.lastAccess, r.lastWrite, riskText});
        }
    }

    public void updateRiskForSelectedRow(int risk) {
        int row = table.getSelectedRow();
        if (row >= 0) model.setValueAt(String.valueOf(risk), row, COL_RISK);
    }

    public void updatePathForSelectedRow(String newPath) {
        int row = table.getSelectedRow();
        if (row >= 0) model.setValueAt(newPath, row, COL_PATH);
    }

    public void removeSelectedRow() {
        int row = table.getSelectedRow();
        if (row >= 0) model.removeRow(row);
    }

    public boolean confirmDelete(String path) {
        int confirm = JOptionPane.showConfirmDialog(null,
                "确认删除?\n" + path, "删除确认", JOptionPane.YES_NO_OPTION);
        return confirm == JOptionPane.YES_OPTION;
    }

    public String promptNewName(String oldName) {
        String newName = JOptionPane.showInputDialog("输入新文件名:", oldName);
        if (newName == null) return null;
        newName = newName.trim();
        return newName.isEmpty() ? null : newName;
    }

    public void showInfo(String msg) {
        JOptionPane.showMessageDialog(null, msg);
    }

    public JDialog showLoading(String title, String message) {
        final JDialog loading = new JDialog((Frame) null, title, false);
        JLabel msg = new JLabel(message);
        msg.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        loading.add(msg);
        loading.pack();
        loading.setLocationRelativeTo(null);
        loading.setVisible(true);
        return loading;
    }

    private void installDoubleClick() {
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;

                int row = table.getSelectedRow();
                int col = table.getSelectedColumn();
                if (row < 0) return;

                String path = (String) model.getValueAt(row, COL_PATH);
                if (col == COL_RISK) onShowAi.accept(path, true);
                else onOpenPath.accept(path);
            }
        });
    }

    private void installRightClickMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("删除");
        JMenuItem analyseItem = new JMenuItem("分析（写入缓存）");
        JMenuItem showItem = new JMenuItem("查看 AI 字段（不分析）");
        JMenuItem renameItem = new JMenuItem("重命名");

        menu.add(deleteItem);
        menu.add(analyseItem);
        menu.add(showItem);
        menu.add(renameItem);

        deleteItem.addActionListener(e -> {
            String p = getSelectedPath();
            if (p != null) onDelete.accept(p);
        });
        analyseItem.addActionListener(e -> {
            String p = getSelectedPath();
            if (p != null) onShowAi.accept(p, true);
        });
        showItem.addActionListener(e -> {
            String p = getSelectedPath();
            if (p != null) onShowAi.accept(p, false);
        });
        renameItem.addActionListener(e -> {
            String p = getSelectedPath();
            if (p != null) onRename.accept(p);
        });

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
}