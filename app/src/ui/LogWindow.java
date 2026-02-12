// file: src/ui/LogWindow.java
package ui;

import config.OssConfig;
import config.OssConfigStore;
import model.LogEntry;
import oss.OssRecycleService;
import util.LogReader;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogWindow extends JFrame {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // 从 detail 中提取 uploaded=objectKey
    private static final Pattern UPLOADED_KEY = Pattern.compile("uploaded=([^;\\s]+)");

    // 列索引（避免写魔法数字）
    private static final int COL_TIME   = 0;
    private static final int COL_ACTION = 1;
    private static final int COL_PATH   = 2;
    private static final int COL_DETAIL = 3;
    private static final int COL_UNDO   = 4;
    private static final int COL_RAW    = 5; // ✅ 隐藏列：原始日志行

    private final DefaultTableModel model;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JComboBox<String> actionBox;
    private final JTextField pathField;
    private final JTextField fromField;
    private final JTextField toField;

    public LogWindow() {
        super("操作日志");

        ModernTheme.install();

        // Top filter bar
        JPanel top = new JPanel(new GridBagLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        actionBox = new JComboBox<>();
        pathField = new JTextField();
        fromField = new JTextField();
        toField = new JTextField();

        JButton refreshBtn = new JButton("刷新");
        JButton clearBtn = new JButton("清空日志");

        styleInput(pathField);
        styleInput(fromField);
        styleInput(toField);

        pathField.putClientProperty("JTextField.placeholderText", "path 模糊匹配");
        fromField.putClientProperty("JTextField.placeholderText", "from yyyy-MM-dd");
        toField.putClientProperty("JTextField.placeholderText", "to yyyy-MM-dd");

        // ✅ 固定筛选：只允许 ALL/DELETE/RENAME
        actionBox.removeAllItems();
        actionBox.addItem("ALL");
        actionBox.addItem("DELETE");
        actionBox.addItem("RENAME");
        actionBox.setSelectedItem("ALL");

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 10);
        c.gridy = 0;

        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        top.add(new JLabel("操作"), c);

        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.HORIZONTAL;
        actionBox.setPreferredSize(new Dimension(160, 32));
        top.add(actionBox, c);

        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        top.add(new JLabel("Path"), c);

        c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        top.add(pathField, c);

        c.gridx = 4; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        top.add(new JLabel("日期"), c);

        c.gridx = 5; c.weightx = 0; c.fill = GridBagConstraints.HORIZONTAL;
        fromField.setPreferredSize(new Dimension(140, 32));
        top.add(fromField, c);

        c.gridx = 6; c.weightx = 0; c.fill = GridBagConstraints.HORIZONTAL;
        toField.setPreferredSize(new Dimension(140, 32));
        top.add(toField, c);

        c.gridx = 7; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        top.add(refreshBtn, c);

        c.gridx = 8; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        top.add(clearBtn, c);

        // =========================
        // Table (新增 “撤回” 列 + 隐藏 RAW 列)
        // =========================
        model = new DefaultTableModel(new String[]{"Time", "Action", "Path", "Detail", "撤回", "RAW"}, 0) {
            public boolean isCellEditable(int r, int col) {
                return col == COL_UNDO; // 只有撤回列可点
            }
        };

        table = new JTable(model);
        table.setRowHeight(28);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setBackground(new Color(0xF6, 0xF7, 0xFB));
        header.setForeground(new Color(0x3A, 0x3F, 0x50));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2, 0xE6, 0xF0)));

        table.setDefaultRenderer(Object.class, new StripeRenderer());

        // 撤回按钮列：渲染 + 编辑器
        TableColumn undoCol = table.getColumnModel().getColumn(COL_UNDO);
        undoCol.setCellRenderer(new UndoButtonRenderer());
        undoCol.setCellEditor(new UndoButtonEditor(new JCheckBox()));
        undoCol.setMaxWidth(90);
        undoCol.setMinWidth(90);

        // ✅ 隐藏 RAW 列（但保留数据）
        TableColumn rawCol = table.getColumnModel().getColumn(COL_RAW);
        rawCol.setMinWidth(0);
        rawCol.setMaxWidth(0);
        rawCol.setPreferredWidth(0);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)));
        sp.getViewport().setBackground(Color.WHITE);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);

        setContentPane(root);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        // Bind events
        refreshBtn.addActionListener(e -> reload());
        clearBtn.addActionListener(e -> clearLogs());

        actionBox.addActionListener(e -> applyFilter());
        addDocListener(pathField);
        addDocListener(fromField);
        addDocListener(toField);

        // Init
        reload();
        setVisible(true);
    }

    private void reload() {
        List<LogEntry> list = LogReader.readAllDeleteRename();

        model.setRowCount(0);
        for (LogEntry e : list) {
            String ts = TS_FMT.format(e.time);
            String action = e.action;
            String path = e.path;
            String detail = e.detail;

            // ✅ 只有 DELETE 且 detail 含 uploaded=... 才给按钮
            String undo = "";
            if ("DELETE".equals(action) && extractUploadedKey(detail) != null) {
                undo = "撤回";
            }

            // ✅ 第 6 列存 RAW 原始行，供 removeExactLine 精确删除
            model.addRow(new Object[]{ ts, action, path, detail, undo, e.raw });
        }
        applyFilter();
    }

    private void clearLogs() {
        int ok = JOptionPane.showConfirmDialog(
                this,
                "确认清空所有日志？该操作不可恢复。",
                "清空日志",
                JOptionPane.YES_NO_OPTION
        );
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            LogReader.clear();
            reload();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "清空失败: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyFilter() {
        final String action = (String) actionBox.getSelectedItem();
        final String pathKey = safeLower(pathField.getText());
        final LocalDate from = parseDate(fromField.getText());
        final LocalDate to = parseDate(toField.getText());

        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String ts = safe(entry.getValue(COL_TIME));
                String ac = safe(entry.getValue(COL_ACTION));
                String p  = safe(entry.getValue(COL_PATH));

                if (action != null && !"ALL".equals(action) && !action.equals(ac)) return false;
                if (!pathKey.isEmpty() && !safeLower(p).contains(pathKey)) return false;

                if (from != null || to != null) {
                    LocalDate d = parseDateFromTimestamp(ts);
                    if (d == null) return false;
                    if (from != null && d.isBefore(from)) return false;
                    if (to != null && d.isAfter(to)) return false;
                }
                return true;
            }
        });
    }

    private void addDocListener(JTextField tf) {
        tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void fire() { applyFilter(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { fire(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { fire(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { fire(); }
        });
    }

    // =========================
    // 撤回：核心逻辑（完整）
    // =========================
    private void doUndoAtViewRow(int viewRow) {
        int r = table.convertRowIndexToModel(viewRow);

        String action = safe(model.getValueAt(r, COL_ACTION));
        if (!"DELETE".equals(action)) return;

        String path   = safe(model.getValueAt(r, COL_PATH));
        String detail = safe(model.getValueAt(r, COL_DETAIL));
        String rawLine = safe(model.getValueAt(r, COL_RAW)); // ✅ 正确取 RAW 列

        if (rawLine.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "缺少 RAW 日志行，无法从文件中精确删除该条日志。",
                    "撤回失败",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String objectKey = extractUploadedKey(detail);
        if (objectKey == null) {
            JOptionPane.showMessageDialog(this,
                    "该条日志没有 uploaded=objectKey，无法撤回。\n\nDetail:\n" + detail,
                    "撤回失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        OssConfig cfg = OssConfigStore.load();
        if (cfg == null || !cfg.isComplete()) {
            JOptionPane.showMessageDialog(this,
                    "OSS 未配置或不完整。\n请先在主界面里填写 OSS 配置并测试通过。",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        File dest = new File(path);
        if (dest.exists()) {
            int ow = JOptionPane.showConfirmDialog(
                    this,
                    "目标位置已存在文件：\n" + path + "\n\n是否覆盖？",
                    "覆盖确认",
                    JOptionPane.YES_NO_OPTION
            );
            if (ow != JOptionPane.YES_OPTION) return;
        }

        boolean deleteRemoteAfter = JOptionPane.showConfirmDialog(
                this,
                "恢复成功后是否删除 OSS 上的备份对象？\n（建议选“否”，方便重复恢复）",
                "是否删除云端备份",
                JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION;

        // loading dialog
        JDialog loading = new JDialog(this, "正在撤回...", true);
        loading.setLayout(new BorderLayout(8, 8));
        JLabel lb = new JLabel("正在从 OSS 下载并写回本地，请稍候...");
        lb.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        loading.add(lb, BorderLayout.CENTER);
        loading.setSize(480, 160);
        loading.setLocationRelativeTo(this);

        final int modelRowToRemove = r;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // 1) 从 OSS 恢复到原路径
                OssRecycleService.restoreToOriginal(cfg, objectKey, path, deleteRemoteAfter);

                // 2) 恢复成功后删日志（必须在后台做，避免 UI 卡）
                try {
                    boolean removed = LogReader.removeExactLine(rawLine);
                    if (!removed) {
                        // 这里抛异常，让 done() 走失败提示（你也可以改成警告不失败）
                        throw new RuntimeException("日志删除未命中（可能日志文件内容变化）: " + LogReader.getLogFilePath());
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException("删除日志失败: " + ioe.getMessage(), ioe);
                }

                return null;
            }

            @Override
            protected void done() {
                loading.dispose();
                try {
                    get(); // ✅ 没异常 = 恢复 + 删日志 都成功

                    // ✅ UI 移除该行（不必等 reload）
                    if (modelRowToRemove >= 0 && modelRowToRemove < model.getRowCount()) {
                        model.removeRow(modelRowToRemove);
                    }

                    JOptionPane.showMessageDialog(LogWindow.this,
                            "恢复成功：\n" + path,
                            "撤回完成",
                            JOptionPane.INFORMATION_MESSAGE);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LogWindow.this,
                            "撤回失败：\n" + e.getMessage(),
                            "撤回失败",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();

        loading.setVisible(true);
    }

    private static String extractUploadedKey(String detail) {
        if (detail == null) return null;
        Matcher m = UPLOADED_KEY.matcher(detail);
        return m.find() ? m.group(1) : null;
    }

    private static void styleInput(JTextField tf) {
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
    }

    private static LocalDate parseDate(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDateFromTimestamp(String ts) {
        if (ts == null || ts.length() < 10) return null;
        try {
            return LocalDate.parse(ts.substring(0, 10), DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    // =========================
    // Renderer（原样保留）
    // =========================
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

    // =========================
    // 撤回按钮渲染/编辑器
    // =========================
    private final class UndoButtonRenderer extends JButton implements TableCellRenderer {
        UndoButtonRenderer() {
            setOpaque(true);
            setFocusPainted(false);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            String v = value == null ? "" : value.toString();
            if (v.isBlank()) {
                setText("");
                setEnabled(false);
            } else {
                setText(v);
                setEnabled(true);
            }
            return this;
        }
    }

    private final class UndoButtonEditor extends DefaultCellEditor {
        private final JButton button = new JButton();
        private int lastRow = -1;

        UndoButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.addActionListener(e -> {
                fireEditingStopped();
                if (lastRow >= 0) doUndoAtViewRow(lastRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            lastRow = row;
            String v = value == null ? "" : value.toString();
            button.setText(v);
            button.setEnabled(!v.isBlank());
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return button.getText();
        }
    }
}
