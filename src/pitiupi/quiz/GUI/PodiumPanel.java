package pitiupi.quiz.GUI;

import pitiupi.quiz.model.PlayerScore;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pódio final animado: as colunas sobem, os nomes aparecem e cai confete.
 */
final class PodiumPanel extends JComponent {

    private static final long RISE_MS = 1400;

    private List<PlayerScore> players = List.of();
    private String highlightId;
    private long startedAt;
    private final List<float[]> confetti = new ArrayList<>();
    private final Random random = new Random();

    PodiumPanel() {
        setPreferredSize(new Dimension(700, 420));
    }

    void setPlayers(List<PlayerScore> players, String highlightId) {
        boolean changed = !players.equals(this.players);
        this.players = players;
        this.highlightId = highlightId;
        if (changed) {
            restart();
        }
        repaint();
    }

    void restart() {
        startedAt = System.currentTimeMillis();
        confetti.clear();
        for (int i = 0; i < 140; i++) {
            // x, y, velocidade, fase do balanço, índice de cor
            confetti.add(new float[]{random.nextFloat(), -random.nextFloat() * 1.2f,
                    0.15f + random.nextFloat() * 0.25f, random.nextFloat() * 6.28f, random.nextInt(5)});
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Widgets.smooth(g);
        int w = getWidth();
        int h = getHeight();
        long age = System.currentTimeMillis() - startedAt;

        int baseY = h - 90;
        int colW = Math.min(180, w / 4);
        int gap = 16;
        int centerX = w / 2;
        int[] order = {1, 0, 2}; // 2º à esquerda, 1º no meio, 3º à direita
        int[] heights = {(int) (h * 0.42), (int) (h * 0.30), (int) (h * 0.22)};
        Color[] colors = {Theme.GOLD, Theme.SILVER, Theme.BRONZE};

        for (int slot = 0; slot < 3; slot++) {
            int rank = order[slot];
            int x = centerX - colW / 2 + (slot - 1) * (colW + gap);
            double delay = rank == 0 ? 600 : rank == 1 ? 300 : 0; // o 1º sobe por último, para dar suspense
            double t = Math.min(1, Math.max(0, (age - delay) / RISE_MS));
            int colH = (int) (heights[rank] * RankingPanel.easeOut(t));

            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(x + 4, baseY - colH + 6, colW, colH, 18, 18);
            g2.setColor(colors[rank]);
            g2.fillRoundRect(x, baseY - colH, colW, colH, 18, 18);

            if (t > 0.2) {
                g2.setColor(new Color(0, 0, 0, 110));
                g2.setFont(Theme.font(44, true));
                String pos = (rank + 1) + "º";
                int pw = g2.getFontMetrics().stringWidth(pos);
                g2.drawString(pos, x + (colW - pw) / 2, baseY - colH + 54);
            }

            if (rank < players.size() && t >= 1) {
                PlayerScore p = players.get(rank);
                boolean me = p.getId().equals(highlightId);
                g2.setFont(Theme.font(rank == 0 ? 24 : 20, true));
                g2.setColor(me ? Theme.GOLD : Color.WHITE);
                String name = fit(g2, p.getName(), colW + 40);
                int nw = g2.getFontMetrics().stringWidth(name);
                g2.drawString(name, x + (colW - nw) / 2, baseY - colH - 34);
                g2.setFont(Theme.font(16, false));
                g2.setColor(Theme.TEXT_SOFT);
                String pts = p.getScore() + " pts";
                int sw = g2.getFontMetrics().stringWidth(pts);
                g2.drawString(pts, x + (colW - sw) / 2, baseY - colH - 12);
            } else if (rank >= players.size() && t >= 1) {
                g2.setFont(Theme.font(16, false));
                g2.setColor(Theme.TEXT_SOFT);
                int nw = g2.getFontMetrics().stringWidth("—");
                g2.drawString("—", x + (colW - nw) / 2, baseY - colH - 14);
            }
        }

        // Do 4º em diante, uma linha embaixo.
        if (players.size() > 3 && age > RISE_MS + 600) {
            StringBuilder rest = new StringBuilder();
            for (int i = 3; i < Math.min(players.size(), 8); i++) {
                if (rest.length() > 0) {
                    rest.append("   ·   ");
                }
                rest.append(i + 1).append("º ").append(players.get(i).getName()).append(" (").append(players.get(i).getScore()).append(")");
            }
            g2.setFont(Theme.font(16, false));
            g2.setColor(Theme.TEXT_SOFT);
            String text = fit(g2, rest.toString(), w - 20);
            int tw = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, (w - tw) / 2, baseY + 40);
        }

        if (age > RISE_MS + 600) {
            Color[] palette = {Theme.OPTION_COLORS[0], Theme.OPTION_COLORS[1], Theme.OPTION_COLORS[2], Theme.OPTION_COLORS[3], Theme.GOLD};
            double seconds = (age - RISE_MS - 600) / 1000.0;
            for (float[] c : confetti) {
                double y = (c[1] + seconds * c[2]) % 1.3;
                if (y < 0) {
                    continue;
                }
                double x = c[0] + Math.sin(seconds * 3 + c[3]) * 0.015;
                g2.setColor(palette[(int) c[4]]);
                int px = (int) (x * w);
                int py = (int) (y * h);
                g2.fillRect(px, py, 7, 11);
            }
        }
        g2.dispose();
    }

    private static String fit(Graphics2D g2, String text, int width) {
        String shown = text;
        while (g2.getFontMetrics().stringWidth(shown) > width && shown.length() > 4) {
            shown = shown.substring(0, shown.length() - 2);
        }
        return shown.equals(text) ? text : shown + "…";
    }
}
