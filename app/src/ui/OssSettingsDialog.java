package ui;

import config.OssConfig;
import config.OssConfigStore;
import oss.OssRecycleService;

import javax.swing.*;
import java.awt.*;

public final class OssSettingsDialog {

    private OssSettingsDialog() {}

    public static void show(Window parent, boolean mustSetup) {
        OssConfig cur = OssConfigStore.load();
        if (cur == null) cur = new OssConfig();

        JTextField endpoint = new JTextField(nvl(cur.endpoint, ""));
        JTextField region = new JTextField(nvl(cur.region, "cn-hangzhou"));
        JTextField bucket = new JTextField(nvl(cur.bucket, "ai-curator"));
        JTextField ak = new JTextField(nvl(cur.accessKeyId, ""));
        JPasswordField sk = new JPasswordField(nvl(cur.accessKeySecret, ""));
        JCheckBox cname = new JCheckBox("Endpoint 是 CNAME（自定义域名）", cur.useCname);

        Font f13 = new Font("Microsoft YaHei", Font.PLAIN, 13);
        endpoint.setFont(f13);
        region.setFont(f13);
        bucket.setFont(f13);
        ak.setFont(f13);
        sk.setFont(f13);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, g, row++, "Endpoint", endpoint,
                "例：https://oss-cn-hangzhou.aliyuncs.com 或 https://cn-hangzhou.taihangqzs.cn");
        addRow(form, g, row++, "", cname, "勾选表示 endpoint 为自定义域名");
        addRow(form, g, row++, "Region", region, "V4 必填，如 cn-hangzhou");
        addRow(form, g, row++, "Bucket", bucket, "例：ai-curator");
        addRow(form, g, row++, "AccessKeyId", ak, "RAM 用户 AK（永久）");
        addRow(form, g, row++, "AccessKeySecret", sk, "RAM 用户 SK（永久）");

        JButton testBtn = new JButton("测试连接");
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        btns.add(testBtn);
        btns.add(saveBtn);
        btns.add(cancelBtn);

        JLabel tips = new JLabel("提示：本项目使用 V1 3.17.4 + V4 签名，region 必须与 bucket 所在地域一致。");
        tips.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        tips.setForeground(new Color(0x66, 0x66, 0x66));

        JDialog dlg = new JDialog(parent, "OSS 设置", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(tips, BorderLayout.CENTER);
        bottom.add(btns, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        dlg.add(bottom, BorderLayout.SOUTH);

        dlg.setSize(980, 460);
        dlg.setLocationRelativeTo(parent);

        final boolean[] testedOk = {false};

        testBtn.addActionListener(e -> {
            OssConfig cfg;
            try {
                cfg = read(endpoint, region, bucket, ak, sk, cname);
            } catch (Exception ex) {
                testedOk[0] = false;
                JOptionPane.showMessageDialog(dlg, ex.getMessage(), "配置错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            testBtn.setEnabled(false);
            saveBtn.setEnabled(false);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    OssRecycleService.testConnection(cfg);
                    return null;
                }

                @Override
                protected void done() {
                    testBtn.setEnabled(true);
                    saveBtn.setEnabled(true);

                    try {
                        get();
                        testedOk[0] = true;
                        JOptionPane.showMessageDialog(dlg, "测试通过 ✅", "OK", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        testedOk[0] = false;
                        String msg = unwrap(ex);
                        if (msg != null && msg.contains("javax.xml.bind")) {
                            msg = msg + "\n\n检测到 JAXB 缺失：若你使用 Java 9+，需要加入 jaxb-api/activation/jaxb-runtime 依赖。";
                        }
                        JOptionPane.showMessageDialog(dlg, msg, "测试失败", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        saveBtn.addActionListener(e -> {
            OssConfig cfg;
            try {
                cfg = read(endpoint, region, bucket, ak, sk, cname);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, ex.getMessage(), "配置错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!testedOk[0]) {
                int x = JOptionPane.showConfirmDialog(dlg, "尚未测试通过，仍要保存吗？", "提示", JOptionPane.YES_NO_OPTION);
                if (x != JOptionPane.YES_OPTION) return;
            }

            try {
                OssConfigStore.save(cfg);
                JOptionPane.showMessageDialog(dlg, "已保存 ✅", "OK", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "保存失败：" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> {
            if (mustSetup) {
                JOptionPane.showMessageDialog(dlg, "首次使用必须完成 OSS 配置。");
                return;
            }
            dlg.dispose();
        });

        dlg.setVisible(true);
    }

    private static OssConfig read(JTextField endpoint, JTextField region, JTextField bucket,
                                 JTextField ak, JPasswordField sk, JCheckBox cname) {
        OssConfig c = new OssConfig();
        c.endpoint = trim(endpoint.getText());
        c.region = trim(region.getText());
        c.bucket = trim(bucket.getText());
        c.accessKeyId = trim(ak.getText());
        c.accessKeySecret = trim(new String(sk.getPassword()));
        c.useCname = cname.isSelected();

        if (c.endpoint.isEmpty() || c.region.isEmpty() || c.bucket.isEmpty()
                || c.accessKeyId.isEmpty() || c.accessKeySecret.isEmpty()) {
            throw new RuntimeException("endpoint / region / bucket / AK / SK 均不能为空。");
        }

        if (!c.endpoint.startsWith("http://") && !c.endpoint.startsWith("https://")) {
            c.endpoint = "https://" + c.endpoint;
        }

        return c;
    }

    private static void addRow(JPanel p, GridBagConstraints g, int row,
                              String labelText, JComponent field, String help) {
        g.gridy = row;

        g.gridx = 0;
        g.weightx = 0;
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        p.add(label, g);

        g.gridx = 1;
        g.weightx = 1;
        p.add(field, g);

        g.gridx = 2;
        g.weightx = 0;
        JLabel h = new JLabel(help == null ? "" : help);
        h.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        h.setForeground(new Color(0x77, 0x77, 0x77));
        p.add(h, g);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static String nvl(String s, String def) { return (s == null || s.isBlank()) ? def : s; }

    private static String unwrap(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
