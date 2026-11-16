package pitiupi.quiz.GUI;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Uma alternativa: bloco colorido com forma, texto e, na revelação, a barra de votos.
 */
final class OptionTile extends JComponent {

    enum Mode {
        /** Pode clicar (jogador) ou só exibir (apresentador). */
        NORMAL,
        /** O jogador escolheu esta. */
        CHOSEN,
        /** Apagada: outra foi escolhida, ou não é a certa. */
        DIMMED,
        /** Revelação: esta é a correta. */
        CORRECT
    }

    private final int index;
    private String text = "";
    private Mode mode = Mode.NORMAL;
    private boolean clickable;
    private boolean hover;
    private int votes = -1;
    private int maxVotes = 1;
    private IntConsumer onClick = i -> { };

    OptionTile(int index) {
        this.index = index;
        setPreferredSize(new Dimension(300, 110));
        setFont(Theme.font(22, true));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (clickable) {
                    onClick.accept(OptionTile.this.index);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    void setOnClick(IntConsumer onClick) {
        this.onClick = onClick;
    }

    void show(String text, Mode mode, boolean clickable, int votes, int maxVotes) {
        this.text = text == null ? "" : text;
        this.mode = mode;
        this.clickable = clickable;
        this.votes = votes;
        this.maxVotes = Math.max(1, maxVotes);
        setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Widgets.smooth(g);
        int w = getWidth();
        int h = getHeight();
        Color base = Theme.OPTION_COLORS[index];
        if (mode == Mode.DIMMED) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        }
        Color fill = (clickable && hover) ? base.brighter() : base;

        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRoundRect(0, 5, w, h - 5, 24, 24);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w, h - 5, 24, 24);

        if (mode == Mode.CHOSEN || mode == Mode.CORRECT) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(6f));
            g2.drawRoundRect(3, 3, w - 7, h - 11, 22, 22);
        }

        int iconSize = Math.min(44, h - 30);
        int iconX = 18;
        int iconY = (h - 5 - iconSize) / 2;
        drawShape(g2, iconX, iconY, iconSize);

        int textX = iconX + iconSize + 16;
        int right = mode == Mode.CORRECT ? 54 : 18;
        int barSpace = votes >= 0 ? 26 : 0;
        int textWidth = Math.max(40, w - textX - right);
        g2.setColor(Color.WHITE);
        int fontSize = 22;
        List<String> lines;
        FontMetrics fm;
        do {
            g2.setFont(Theme.font(fontSize, true));
            fm = g2.getFontMetrics();
            lines = wrap(text, fm, textWidth);
            fontSize -= 2;
        } while (lines.size() * fm.getHeight() > h - 18 - barSpace && fontSize >= 12);
        int blockHeight = lines.size() * fm.getHeight();
        int y = (h - 5 - barSpace - blockHeight) / 2 + fm.getAscent();
        for (String line : lines) {
            g2.drawString(line, textX, y);
            y += fm.getHeight();
        }

        if (votes >= 0) {
            int barX = textX;
            int barW = Math.max(30, w - textX - (mode == Mode.CORRECT ? 150 : 100));
            int barY = h - 5 - 22;
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(barX, barY, barW, 12, 12, 12);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(barX, barY, (int) Math.round(barW * (double) votes / maxVotes), 12, 12, 12);
            g2.setFont(Theme.font(16, true));
            String count = votes + (votes == 1 ? " voto" : " votos");
            g2.drawString(count, barX + barW + 8, barY + 12);
        }

        if (mode == Mode.CORRECT) {
            g2.setFont(Theme.font(36, true));
            g2.setColor(Color.WHITE);
            g2.drawString("✔", w - 48, (h - 5) / 2 + 13);
        }
        g2.dispose();
    }

    private void drawShape(Graphics2D g2, int x, int y, int s) {
        g2.setColor(Color.WHITE);
        switch (index) {
            case 0 -> g2.fillPolygon(new Polygon(new int[]{x + s / 2, x + s, x}, new int[]{y, y + s, y + s}, 3));
            case 1 -> g2.fillPolygon(new Polygon(new int[]{x + s / 2, x + s, x + s / 2, x}, new int[]{y, y + s / 2, y + s, y + s / 2}, 4));
            case 2 -> g2.fillOval(x, y, s, s);
            default -> g2.fillRect(x + 3, y + 3, s - 6, s - 6);
        }
    }

    static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
