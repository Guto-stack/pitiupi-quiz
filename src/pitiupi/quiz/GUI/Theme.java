package pitiupi.quiz.GUI;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;

/**
 * Cores, fontes e fábricas de componentes do Quiz.
 */
final class Theme {

    static final Color BG_TOP = new Color(0x2B0E5C);
    static final Color BG_BOTTOM = new Color(0x4A1C96);
    static final Color CARD = new Color(255, 255, 255, 28);
    static final Color CARD_BORDER = new Color(255, 255, 255, 60);
    static final Color TEXT = Color.WHITE;
    static final Color TEXT_SOFT = new Color(255, 255, 255, 190);
    static final Color ACCENT = new Color(0x26890C);
    static final Color DANGER = new Color(0xC8102E);
    static final Color NEUTRAL = new Color(0x6B5B95);
    static final Color GOLD = new Color(0xF2C14E);
    static final Color SILVER = new Color(0xC0C7D0);
    static final Color BRONZE = new Color(0xCD7F32);

    /** Vermelho, azul, amarelo, verde — uma cor e uma forma por alternativa. */
    static final Color[] OPTION_COLORS = {
            new Color(0xE21B3C),
            new Color(0x1368CE),
            new Color(0xD89E00),
            new Color(0x26890C)
    };
    static final String[] OPTION_SHAPES = {"▲", "◆", "●", "■"};

    private static final String FAMILY = Font.SANS_SERIF;

    private Theme() {
    }

    static Font font(int size, boolean bold) {
        return new Font(FAMILY, bold ? Font.BOLD : Font.PLAIN, size);
    }

    static JLabel label(String text, int size, boolean bold) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(font(size, bold));
        return l;
    }

    static JLabel centered(String text, int size, boolean bold) {
        JLabel l = label(text, size, bold);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return l;
    }

    /** Texto que quebra linha sozinho, centralizado, sem fundo. */
    static JTextPane wrapping(int size, boolean bold) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setForeground(TEXT);
        pane.setFont(font(size, bold));
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        StyleConstants.setForeground(center, TEXT);
        StyleConstants.setFontFamily(center, FAMILY);
        StyleConstants.setFontSize(center, size);
        StyleConstants.setBold(center, bold);
        doc.setParagraphAttributes(0, doc.getLength(), center, false);
        pane.putClientProperty("quiz.style", center);
        return pane;
    }

    static void setWrappingText(JTextPane pane, String text) {
        String value = text == null ? "" : text;
        if (value.equals(pane.getText())) {
            return;
        }
        pane.setText(value);
        Object style = pane.getClientProperty("quiz.style");
        if (style instanceof SimpleAttributeSet attrs) {
            StyledDocument doc = pane.getStyledDocument();
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
            doc.setCharacterAttributes(0, doc.getLength(), attrs, false);
        }
    }

    static String ordinal(int position) {
        return position + "º";
    }
}
