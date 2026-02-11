package ui;

import model.FileRecord;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
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

    private volatile boolean interactionsEnabled = true;

    private Consumer<String> onOpenPath = p -> {};
    private BiConsumer<String, Boolean> onShowAi = (p, allow) -> {};
    private Consumer<String> onDelete = p -> {};
    private Consumer<String> onRename = p -> {};

    public FileTableView() {
        String[] cols = {"Path", "Size(byte)", "Created", "LastAccess", "LastWrite", "Risk(0-100)"};
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(28);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setBackground(new Color(0xF6, 0xF7, 0xFB));
        header.setForeground(new Color(0x3A, 0x3F, 0x50));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2, 0xE6, 0xF0)));

        // Subtle row striping
        table.setDefaultRenderer(Object.class, new StripeRenderer());

        installDoubleClick();
        installRightClickMenu();
    }

    public JTable getTable() { return table; }

    public void setOnOpenPath(Consumer<String> cb) { this.onOpenPath = cb; }
    public void setOnShowAi(BiConsumer<String, Boolean> cb) { this.onShowAi = cb; }
    public void setOnDelete(Consumer<String> cb) { this.onDelete = cb; }
    public void setOnRename(Consumer<String> cb) { this.onRename = cb; }

    public void setInteractionsEnabled(boolean enabled) {
        this.interactionsEnabled = enabled;
        table.setEnabled(enabled);
        table.getTableHeader().setEnabled(enabled);
    }

    public boolean isInteractionsEnabled() {
        return interactionsEnabled;
    }

    public DocumentListener createSearchListener(Consumer<String> onQueryChanged, JTextField tf) {
        return new DocumentListener() {
            private void refresh() {
                if (!interactionsEnabled) return;
                onQueryChanged.accept(tf.getText());
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        };
    }

    public String getSelectedPath() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return (String) model.getValueAt(modelRow, COL_PATH);
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
        if (row >= 0) {
            int modelRow = table.convertRowIndexToModel(row);
            model.setValueAt(String.valueOf(risk), modelRow, COL_RISK);
        }
    }

    public void updatePathForSelectedRow(String newPath) {
        int row = table.getSelectedRow();
        if (row >= 0) {
            int modelRow = table.convertRowIndexToModel(row);
            model.setValueAt(newPath, modelRow, COL_PATH);
        }
    }

    public void removeSelectedRow() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            int modelRow = table.convertRowIndexToModel(row);
            model.removeRow(modelRow);
        }
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
        loading.setLayout(new BorderLayout());
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBorderPainted(false);

        JLabel msg = new JLabel(message);
        msg.setForeground(new Color(0x55, 0x5A, 0x6A));

        p.add(msg, BorderLayout.NORTH);
        p.add(bar, BorderLayout.SOUTH);

        loading.add(p, BorderLayout.CENTER);
        loading.pack();
        loading.setLocationRelativeTo(null);
        loading.setAlwaysOnTop(true);
        loading.setVisible(true);
        return loading;
    }

    private void installDoubleClick() {
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!interactionsEnabled) return;
                if (e.getClickCount() != 2) return;

                int row = table.getSelectedRow();
                int col = table.getSelectedColumn();
                if (row < 0) return;

                int modelRow = table.convertRowIndexToModel(row);
                String path = (String) model.getValueAt(modelRow, COL_PATH);
                int modelCol = table.convertColumnIndexToModel(col);

                if (modelCol == COL_RISK) onShowAi.accept(path, true);
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
            if (!interactionsEnabled) return;
            String p = getSelectedPath();
            if (p != null) onDelete.accept(p);
        });
        analyseItem.addActionListener(e -> {
            if (!interactionsEnabled) return;
            String p = getSelectedPath();
            if (p != null) onShowAi.accept(p, true);
        });
        showItem.addActionListener(e -> {
            if (!interactionsEnabled) return;
            String p = getSelectedPath();
            if (p != null) onShowAi.accept(p, false);
        });
        renameItem.addActionListener(e -> {
            if (!interactionsEnabled) return;
            String p = getSelectedPath();
            if (p != null) onRename.accept(p);
        });

        table.addMouseListener(new MouseAdapter() {
            private void showPopup(MouseEvent e) {
                if (!interactionsEnabled) return;
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

    private static final class StripeRenderer extends JLabel implements TableCellRenderer {
        private final Color alt = UIManager.getColor("Table.alternateRowColor") != null
                ? UIManager.getColor("Table.alternateRowColor")
                : new Color(0xFA, 0xFB, 0xFE);

        StripeRenderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setForeground(table.getForeground());
                setBackground((row % 2 == 0) ? Color.WHITE : alt);
            }
            return this;
        }
    }
}
