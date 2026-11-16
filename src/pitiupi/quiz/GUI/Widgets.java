package pitiupi.quiz.GUI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * Componentes pintados à mão: fundo em degradê, cartão translúcido, botão arredondado e barra de tempo.
 */
final class Widgets {

    private Widgets() {
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    /** Painel com o fundo roxo em degradê. */
    static class GradientPanel extends JPanel {
        GradientPanel(LayoutManager layout) {
            super(layout);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, getHeight(), Theme.BG_BOTTOM));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** Cartão translúcido de cantos arredondados. */
    static class Card extends JPanel {
        Card(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g2.setColor(Theme.CARD_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g2.dispose();
        }
    }

    /** Botão arredondado colorido. */
    static class PillButton extends JButton {
        private final Color base;

        PillButton(String text, Color base, int fontSize) {
            super(text);
            this.base = base;
            setFont(Theme.font(fontSize, true));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Color c = base;
            if (!isEnabled()) {
                c = new Color(255, 255, 255, 40);
            } else if (getModel().isPressed()) {
                c = base.darker();
            } else if (getModel().isRollover()) {
                c = base.brighter();
            }
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(0, 4, getWidth(), getHeight() - 4, getHeight() - 4, getHeight() - 4);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight() - 4, getHeight() - 4, getHeight() - 4);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Barra de contagem regressiva: verde → amarelo → vermelho. */
    static class CountdownBar extends JComponent {
        private long deadline;
        private long totalMs = 1;
        private boolean frozen;

        CountdownBar() {
            setPreferredSize(new Dimension(400, 34));
            setMinimumSize(new Dimension(100, 34));
        }

        void start(long deadline, long totalMs) {
            this.deadline = deadline;
            this.totalMs = Math.max(1, totalMs);
            this.frozen = false;
            repaint();
        }

        void freeze() {
            frozen = true;
            repaint();
        }

        long remaining() {
            return frozen ? 0 : Math.max(0, deadline - System.currentTimeMillis());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            long remaining = remaining();
            double fraction = Math.min(1, (double) remaining / totalMs);

            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(0, 0, w, h, h, h);

            Color fill = fraction > 0.5 ? new Color(0x2ECC71) : fraction > 0.25 ? new Color(0xF1C40F) : new Color(0xE74C3C);
            int fw = (int) Math.round(w * fraction);
            if (fw > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, Math.max(fw, h), h, h, h);
            }

            String text = frozen ? "Tempo encerrado" : (int) Math.ceil(remaining / 1000.0) + " s";
            g2.setFont(Theme.font(18, true));
            int tw = g2.getFontMetrics().stringWidth(text);
            g2.setColor(new Color(0, 0, 0, 120));
            g2.drawString(text, (w - tw) / 2 + 1, h / 2 + 7);
            g2.setColor(Color.WHITE);
            g2.drawString(text, (w - tw) / 2, h / 2 + 6);
            g2.dispose();
        }
    }

    /** Selo com o nome de um jogador. */
    static class Chip extends JComponent {
        private final String text;
        private final Color color;

        Chip(String text, Color color) {
            this.text = text;
            this.color = color;
            setFont(Theme.font(16, true));
            int w = getFontMetrics(getFont()).stringWidth(text) + 32;
            setPreferredSize(new Dimension(Math.min(w, 260), 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, getHeight(), getHeight());
            g2.setColor(Color.WHITE);
            g2.setFont(getFont());
            String shown = text;
            while (g2.getFontMetrics().stringWidth(shown) > getWidth() - 24 && shown.length() > 3) {
                shown = shown.substring(0, shown.length() - 2);
            }
            if (!shown.equals(text)) {
                shown = shown + "…";
            }
            int tw = g2.getFontMetrics().stringWidth(shown);
            g2.drawString(shown, (getWidth() - tw) / 2, getHeight() / 2 + 6);
            g2.dispose();
        }
    }
}
