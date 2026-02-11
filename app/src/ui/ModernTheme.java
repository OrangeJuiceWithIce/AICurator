package ui;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

/**
 * Lightweight modern theme without extra dependencies.
 * Uses Nimbus LAF + unified font + subtle paddings.
 */
public final class ModernTheme {
    private ModernTheme() {}

    public static void install() {
        try {
            // Prefer Nimbus for a cleaner default look.
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        // Global font (Windows: Microsoft YaHei; fallback: SansSerif)
        Font base = pickFont(new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "SansSerif"}, 14);
        setUIFont(new FontUIResource(base));

        // Nimbus tweaks (safe even if not Nimbus)
        UIManager.put("control", new Color(0xF6, 0xF7, 0xFB));
        UIManager.put("info", new Color(0xF6, 0xF7, 0xFB));
        UIManager.put("nimbusBase", new Color(0x2A, 0x2F, 0x45));
        UIManager.put("nimbusBlueGrey", new Color(0xE6, 0xE9, 0xF2));
        UIManager.put("nimbusLightBackground", Color.WHITE);
        UIManager.put("text", new Color(0x18, 0x1B, 0x2A));

        UIManager.put("Table.alternateRowColor", new Color(0xFA, 0xFB, 0xFE));
        UIManager.put("Table.gridColor", new Color(0xEC, 0xEE, 0xF6));
        UIManager.put("Table.selectionBackground", new Color(0xD9, 0xE6, 0xFF));
        UIManager.put("Table.selectionForeground", new Color(0x18, 0x1B, 0x2A));
        UIManager.put("Table.showGrid", Boolean.FALSE);

        UIManager.put("ScrollBar.thumb", new Color(0xC8, 0xCE, 0xDD));
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(0xC8, 0xCE, 0xDD));
        UIManager.put("ScrollBar.thumbHighlight", new Color(0xC8, 0xCE, 0xDD));
        UIManager.put("ScrollBar.thumbShadow", new Color(0xC8, 0xCE, 0xDD));
    }

    private static Font pickFont(String[] candidates, int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        var available = java.util.Set.of(ge.getAvailableFontFamilyNames());
        for (String c : candidates) {
            if (available.contains(c)) return new Font(c, Font.PLAIN, size);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    private static void setUIFont(FontUIResource f) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }
}
