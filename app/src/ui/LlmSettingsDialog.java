package ui;

import config.LlmConfig;
import config.LlmConfigStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LlmSettingsDialog extends JDialog {

    private final JTextField baseUrlField = new JTextField();
    private final JTextField modelField = new JTextField();
    private final JPasswordField apiKeyField = new JPasswordField();
    private final JTextField tempField = new JTextField();
    private final JTextField timeoutField = new JTextField();
    private final JComboBox<String> providerBox = new JComboBox<>(new String[]{"deepseek", "openai_compat"});

    private final JLabel pathHint = new JLabel();

    public LlmSettingsDialog(Frame owner) {
        super(owner, "LLM 设置", true);

        ModernTheme.install();

        setLayout(new BorderLayout());
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.setBackground(Color.WHITE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        // provider
        addRow(form, c, "Provider", providerBox);

        // baseUrl
        addRow(form, c, "Base URL", baseUrlField);

        // model
        addRow(form, c, "Model", modelField);

        // apiKey
        addRow(form, c, "API Key", apiKeyField);

        // temperature
        addRow(form, c, "Temperature", tempField);

        // timeout
        addRow(form, c, "Timeout(s)", timeoutField);

        // hint path
        pathHint.setForeground(new Color(0x55, 0x5A, 0x6A));
        pathHint.setText("配置文件: " + LlmConfigStore.getFilePath());
        pathHint.setBorder(new EmptyBorder(8, 6, 0, 6));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setOpaque(false);

        JButton testBtn = new JButton("测试连接");
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");

        bottom.add(testBtn);
        bottom.add(saveBtn);
        bottom.add(cancelBtn);

        root.add(form, BorderLayout.CENTER);
        root.add(pathHint, BorderLayout.NORTH);
        root.add(bottom, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);

        // load config -> fill
        fillFromConfig(LlmConfigStore.load());

        testBtn.addActionListener(e -> onTest());
        saveBtn.addActionListener(e -> onSave());
        cancelBtn.addActionListener(e -> dispose());

        setSize(720, 360);
        setLocationRelativeTo(owner);
    }

    private void addRow(JPanel form, GridBagConstraints c, String label, JComponent input) {
        c.gridx = 0;
        c.weightx = 0;
        form.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        input.setPreferredSize(new Dimension(420, 30));
        styleInput(input);
        form.add(input, c);

        c.gridy++;
    }

    private void styleInput(JComponent comp) {
        if (comp instanceof JTextField tf) {
            tf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
        } else if (comp instanceof JPasswordField pf) {
            pf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
        } else if (comp instanceof JComboBox<?> cb) {
            cb.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)));
        }
    }

    private void fillFromConfig(LlmConfig cfg) {
        if (cfg == null) cfg = new LlmConfig();
        providerBox.setSelectedItem(cfg.provider == null ? "deepseek" : cfg.provider);
        baseUrlField.setText(nullToEmpty(cfg.baseUrl));
        modelField.setText(nullToEmpty(cfg.model));
        apiKeyField.setText(nullToEmpty(cfg.apiKey));
        tempField.setText(String.valueOf(cfg.temperature <= 0 ? 0.1 : cfg.temperature));
        timeoutField.setText(String.valueOf(cfg.timeoutSeconds <= 0 ? 20 : cfg.timeoutSeconds));
    }

    private LlmConfig collectToConfig() {
        LlmConfig c = new LlmConfig();
        c.provider = (String) providerBox.getSelectedItem();
        c.baseUrl = baseUrlField.getText().trim();
        c.model = modelField.getText().trim();
        c.apiKey = new String(apiKeyField.getPassword()).trim();
        c.temperature = parseDouble(tempField.getText().trim(), 0.1);
        c.timeoutSeconds = parseInt(timeoutField.getText().trim(), 20);
        return c;
    }

    private void onSave() {
        LlmConfig cfg = collectToConfig();
        if (!cfg.isComplete()) {
            JOptionPane.showMessageDialog(this,
                    "配置不完整：baseUrl/apiKey/model 必填。",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            LlmConfigStore.save(cfg);
            JOptionPane.showMessageDialog(this,
                    "保存成功。\n\n" + LlmConfigStore.getFilePath(),
                    "OK",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "保存失败: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onTest() {
        LlmConfig cfg = collectToConfig();
        if (!cfg.isComplete()) {
            JOptionPane.showMessageDialog(this,
                    "配置不完整：baseUrl/apiKey/model 必填。",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog loading = new JDialog(this, "测试连接...", true);
        loading.setLayout(new BorderLayout(10, 10));
        JLabel lb = new JLabel("正在请求 /chat/completions ...");
        lb.setBorder(new EmptyBorder(12, 12, 12, 12));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        loading.add(lb, BorderLayout.NORTH);
        loading.add(bar, BorderLayout.CENTER);
        loading.setSize(420, 160);
        loading.setLocationRelativeTo(this);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return testChatCompletions(cfg);
            }

            @Override
            protected void done() {
                loading.dispose();
                try {
                    String content = get();
                    JOptionPane.showMessageDialog(LlmSettingsDialog.this,
                            "测试通过。\n\n返回 content(截断):\n" + clip(content, 300),
                            "OK",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LlmSettingsDialog.this,
                            "测试失败：\n" + ex.getMessage(),
                            "Fail",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();

        loading.setVisible(true);
    }

    private static String testChatCompletions(LlmConfig cfg) throws Exception {
        String url = normalizeBaseUrl(cfg.baseUrl) + "/chat/completions";

        String jsonReq = """
                {
                  "model": "%s",
                  "messages": [
                    { "role": "user", "content": "ping" }
                  ],
                  "temperature": %s,
                  "stream": false
                }
                """.formatted(escape(cfg.model), cfg.temperature);

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        String body = resp.body();

        if (code / 100 != 2) {
            // 直接把响应丢出来便于排查（截断）
            throw new RuntimeException("HTTP " + code + "\n" + clip(body, 800));
        }

        // 最简单提取 content（不引 JSON 库）
        String content = extractContent(body);
        if (content == null || content.isBlank()) {
            throw new RuntimeException("响应中未提取到 content。\n" + clip(body, 800));
        }
        return content;
    }

    // ========= helpers =========

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    // JSON string escape（模型名基本不需要，但保持一致）
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 提取 choices[0].message.content（OpenAI 风格）
     * {"choices":[{"message":{"content":"..."}}]}
     */
    private static String extractContent(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"content\"");
        if (idx < 0) return null;

        int a = json.indexOf("\"", idx + 9);
        if (a < 0) return null;

        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = a + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                esc = false;
            } else {
                if (c == '\\') esc = true;
                else if (c == '"') break;
                else out.append(c);
            }
        }
        return out.toString().trim();
    }
}
