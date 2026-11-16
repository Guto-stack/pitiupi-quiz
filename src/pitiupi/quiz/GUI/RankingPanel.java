package pitiupi.quiz.GUI;

import pitiupi.quiz.model.PlayerScore;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Placar parcial: as primeiras posições, com os pontos ganhos na última pergunta.
 */
final class RankingPanel extends JComponent {

    private static final int ROW = 46;

    private List<PlayerScore> players = List.of();
    private String highlightId;
    private int max = 5;
    private long animatedAt;

    RankingPanel() {
        setPreferredSize(new Dimension(360, ROW * 5 + 40));
    }

    void setPlayers(List<PlayerScore> players, String highlightId, int max) {
        if (!players.equals(this.players)) {
            animatedAt = System.currentTimeMillis();
        }
        this.players = players;
        this.highlightId = highlightId;
        this.max = max;
        setPreferredSize(new Dimension(360, ROW * Math.max(1, Math.min(max, players.size())) + 40));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Widgets.smooth(g);
        int w = getWidth();
        g2.setFont(Theme.font(18, true));
        g2.setColor(Theme.TEXT_SOFT);
        g2.drawString("Placar", 6, 22);

        int shown = Math.min(max, players.size());
        int top = players.isEmpty() ? 1 : Math.max(1, players.get(0).getScore());
        long age = System.currentTimeMillis() - animatedAt;
        for (int i = 0; i < shown; i++) {
            PlayerScore p = players.get(i);
            int y = 34 + i * ROW;
            // Entrada escalonada: cada linha desliza um pouco depois da anterior.
            double t = Math.min(1, Math.max(0, (age - i * 90) / 350.0));
            int slide = (int) ((1 - easeOut(t)) * 40);
            boolean me = p.getId().equals(highlightId);

            g2.setColor(me ? new Color(255, 255, 255, 70) : new Color(255, 255, 255, 30));
            g2.fillRoundRect(slide, y, w - slide, ROW - 8, 16, 16);

            int scoreBar = (int) ((w - 24 - slide) * (double) p.getScore() / top * easeOut(t));
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillRoundRect(slide, y, Math.max(0, scoreBar), ROW - 8, 16, 16);

            Color medal = i == 0 ? Theme.GOLD : i == 1 ? Theme.SILVER : i == 2 ? Theme.BRONZE : Theme.NEUTRAL;
            g2.setColor(medal);
            g2.fillOval(slide + 8, y + 6, 26, 26);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(14, true));
            String pos = String.valueOf(i + 1);
            g2.drawString(pos, slide + 21 - g2.getFontMetrics().stringWidth(pos) / 2, y + 24);

            g2.setFont(Theme.font(18, true));
            String name = p.getName() + (me ? " (você)" : "");
            g2.drawString(name, slide + 44, y + 25);

            String score = String.valueOf(p.getScore());
            int sw = g2.getFontMetrics().stringWidth(score);
            g2.drawString(score, w - sw - 12, y + 25);

            if (p.getLastDelta() > 0) {
                g2.setFont(Theme.font(14, true));
                String delta = "+" + p.getLastDelta();
                int dw = g2.getFontMetrics().stringWidth(delta);
                g2.setColor(new Color(0x7CFC9A));
                g2.drawString(delta, w - sw - dw - 22, y + 24);
            }
        }
        if (players.size() > shown) {
            g2.setFont(Theme.font(14, false));
            g2.setColor(Theme.TEXT_SOFT);
            g2.drawString("+ " + (players.size() - shown) + " jogadores", 8, 34 + shown * ROW + 12);
        }
        g2.dispose();
    }

    boolean animating() {
        return System.currentTimeMillis() - animatedAt < 1200;
    }

    static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }
}
