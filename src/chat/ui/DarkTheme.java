package chat.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Shared Dark Studio UI styling helpers and palette constants for both Client and Server windows.
 */
public final class DarkTheme {
    public static final Color BG_DARKEST = new Color(11, 15, 25);
    public static final Color BG_PANEL = new Color(18, 25, 43);
    public static final Color BG_CARD = new Color(28, 38, 64);
    public static final Color BG_INPUT = new Color(15, 21, 36);
    public static final Color BORDER_SUBTLE = new Color(48, 62, 95);
    public static final Color ACCENT_PRIMARY = new Color(99, 102, 241);
    public static final Color ACCENT_CYAN = new Color(6, 182, 212);
    public static final Color ACCENT_SUCCESS = new Color(16, 185, 129);
    public static final Color ACCENT_DANGER = new Color(239, 68, 68);
    public static final Color TEXT_PRIMARY = new Color(241, 245, 249);
    public static final Color TEXT_MUTED = new Color(148, 163, 184);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    private DarkTheme() {
    }

    public static JButton createButton(String text, Color bgColor, Color fgColor) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(bgColor);
        btn.setForeground(fgColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        return btn;
    }

    public static JTextField createTextField(String initialText, int columns) {
        JTextField field = new JTextField(initialText, columns);
        field.setFont(FONT_BODY);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }

    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        );
    }

    public static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
