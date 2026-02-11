package ui;

import model.LogEntry;
import util.LogReader;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogWindow extends JFrame {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

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

        // Table
        model = new DefaultTableModel(new String[]{"Time", "Action", "Path", "Detail"}, 0) {
            public boolean isCellEditable(int r, int col) { return false; }
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
        // ✅ 只读 DELETE/RENAME
        List<LogEntry> list = LogReader.readAllDeleteRename();

        model.setRowCount(0);
        for (LogEntry e : list) {
            model.addRow(new Object[]{
                    TS_FMT.format(e.time),
                    e.action,
                    e.path,
                    e.detail
            });
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
                String ts = (String) entry.getValue(0);
                String ac = safe(entry.getValue(1));
                String p = safe(entry.getValue(2));

                // action filter
                if (action != null && !"ALL".equals(action) && !action.equals(ac)) return false;

                // path contains
                if (!pathKey.isEmpty() && !safeLower(p).contains(pathKey)) return false;

                // date range
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
