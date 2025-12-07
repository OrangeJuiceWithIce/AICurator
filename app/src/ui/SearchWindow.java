package ui;

import db.SQLiteAccessor;
import model.FileRecord;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SearchWindow extends JFrame {

    private final SQLiteAccessor db;
    private final FileTable fileTable;
    private final JTextField searchField;

    public SearchWindow(String dbPath) {
        super("Simple Everything");

        db = new SQLiteAccessor(dbPath);
        fileTable = new FileTable(db);

        searchField = new JTextField();
        searchField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        searchField.getDocument().addDocumentListener(fileTable.createSearchListener(searchField));

        setLayout(new BorderLayout(4, 4));
        add(searchField, BorderLayout.NORTH);
        add(new JScrollPane(fileTable.getTable()), BorderLayout.CENTER);

        setSize(1000, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        refresh("");
    }

    public void refresh(String key) {
        List<FileRecord> records = db.search(key);
        fileTable.update(records);
    }
}
