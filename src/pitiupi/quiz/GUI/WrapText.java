package pitiupi.quiz.GUI;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Texto com quebra de linha desenhado à mão.
 *
 * <p>Substitui o {@code JTextPane}: no Nimbus (o tema do Pitiupi) o {@code JTextPane} pinta fundo
 * branco mesmo com {@code setOpaque(false)}, e o texto branco some. Aqui o fundo é sempre
 * transparente. Se o texto não couber na altura disponível, a fonte diminui até caber.</p>
 */
final class WrapText extends JComponent {

    private final int maxSize;
    private final boolean bold;
    private final boolean centered;
    private String text = "";

    /**
     * @param maxSize  tamanho máximo da fonte
     * @param centered centraliza na horizontal e na vertical; senão alinha no topo à esquerda
     */
    WrapText(int maxSize, boolean bold, boolean centered) {
        this.maxSize = maxSize;
        this.bold = bold;
        this.centered = centered;
        setOpaque(false);
        setForeground(Theme.TEXT);
    }

    void setText(String value) {
        String v = value == null ? "" : value;
        if (!v.equals(text)) {
            text = v;
            revalidate();
            repaint();
        }
    }

    String getText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int w = getWidth() > 0 ? getWidth() : 320;
        FontMetrics fm = getFontMetrics(Theme.font(maxSize, bold));
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) {
            lines += Math.max(1, OptionTile.wrap(paragraph, fm, w).size());
        }
        return new Dimension(w, lines * fm.getHeight() + 4);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Widgets.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int size = maxSize;
        FontMetrics fm;
        List<String> lines;
        do {
            g2.setFont(Theme.font(size, bold));
            fm = g2.getFontMetrics();
            lines = layout(fm, w);
            size -= 2;
        } while (lines.size() * fm.getHeight() > h && size >= 12);

        g2.setColor(getForeground() == null ? Color.WHITE : getForeground());
        int block = lines.size() * fm.getHeight();
        int y = (centered ? Math.max(0, (h - block) / 2) : 0) + fm.getAscent();
        for (String line : lines) {
            int x = centered ? (w - fm.stringWidth(line)) / 2 : 0;
            g2.drawString(line, x, y);
            y += fm.getHeight();
        }
        g2.dispose();
    }

    private List<String> layout(FontMetrics fm, int width) {
        List<String> out = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            List<String> wrapped = OptionTile.wrap(paragraph, fm, width);
            if (wrapped.isEmpty()) {
                out.add("");
            } else {
                out.addAll(wrapped);
            }
        }
        return out;
    }
}
