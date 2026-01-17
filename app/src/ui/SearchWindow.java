package ui;

import db.SQLiteAccessor;

import javax.swing.*;
import java.awt.*;

public class SearchWindow extends JFrame {

    private final SQLiteAccessor db;
    private final FileTableView view;
    private final FileTableController controller;
    private final JTextField searchField;

    public SearchWindow(String dbPath) {
        super("Simple Everything");

        this.db = new SQLiteAccessor(dbPath);
        this.view = new FileTableView();
        this.controller = new FileTableController(db, view);

        this.searchField = new JTextField();
        searchField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        searchField.getDocument().addDocumentListener(
                view.createSearchListener(controller::onQueryChanged, searchField)
        );

        setLayout(new BorderLayout(4, 4));
        add(searchField, BorderLayout.NORTH);
        add(new JScrollPane(view.getTable()), BorderLayout.CENTER);

        setSize(1000, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        // 首次刷新
        controller.onQueryChanged("");
    }
}