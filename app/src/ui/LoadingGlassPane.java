package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public final class LoadingGlassPane extends JComponent {

    private final JLabel titleLabel = new JLabel("Loading...");
    private final JLabel msgLabel = new JLabel("Please wait");
    private final JProgressBar bar = new JProgressBar();

    public LoadingGlassPane() {
        setOpaque(false);
        setLayout(new GridBagLayout());

        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE2, 0xE6, 0xF0)),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)
        ));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        msgLabel.setForeground(new Color(0x55, 0x5A, 0x6A));
        msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        bar.setIndeterminate(true);
        bar.setBorderPainted(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setPreferredSize(new Dimension(320, 10));
        bar.setMaximumSize(new Dimension(360, 10));

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(msgLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(bar);

        add(card, new GridBagConstraints());

        enableEvents(AWTEvent.MOUSE_EVENT_MASK
                | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.MOUSE_WHEEL_EVENT_MASK
                | AWTEvent.KEY_EVENT_MASK);

        addMouseListener(new MouseAdapter() {});
        addMouseMotionListener(new MouseMotionAdapter() {});
        addMouseWheelListener(e -> {});
        addKeyListener(new KeyAdapter() {});
        setFocusTraversalKeysEnabled(false);
        setVisible(false);
    }

    public void showLoading(String title, String message) {
        updateText(title, message);
        setVisible(true);
        requestFocusInWindow();
        repaint();
    }

    public void updateText(String title, String message) {
        titleLabel.setText(title == null ? "Loading..." : title);
        msgLabel.setText(message == null ? "" : message);
        repaint();
    }

    public void hideLoading() {
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.SrcOver.derive(0.32f));
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
