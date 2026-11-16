package pitiupi.quiz.GUI;

import pitiupi.quiz.control.PlayerSnapshot;
import pitiupi.quiz.control.QuizCore;
import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.PlayerScore;
import pitiupi.quiz.model.RoomState;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Tela do jogador: espera, pergunta com botões, resultado da pergunta e pódio.
 */
final class PlayerView extends JPanel {

    private final QuizCore core;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    // espera
    private final JLabel waitTitle = Theme.centered(" ", 34, true);
    private final JLabel waitStatus = Theme.centered(" ", 20, false);
    private final JLabel waitCount = Theme.centered(" ", 18, true);
    private final JPanel chips = new JPanel(new GridLayout(0, 5, 10, 10));
    private List<String> lastChipNames = List.of();

    // pergunta / revelação
    private final GameStage stage;
    private final JLabel answerStatus = Theme.centered(" ", 19, true);
    private final ResultBanner banner = new ResultBanner();
    private final RankingPanel ranking = new RankingPanel();
    private final JLabel position = Theme.centered(" ", 18, true);

    // fim
    private final JLabel endPosition = Theme.centered(" ", 24, true);
    private final PodiumPanel podium = new PodiumPanel();

    private PlayerSnapshot last;
    private String lastCard = "";
    private boolean timeUpShown;

    PlayerView(QuizCore core) {
        super(new BorderLayout());
        this.core = core;
        this.stage = new GameStage(core::answer);
        setOpaque(false);
        deck.setOpaque(false);
        deck.add(buildWaiting(), "wait");
        deck.add(buildGame(), "game");
        deck.add(buildEnd(), "end");
        add(deck, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
        footer.setOpaque(false);
        Widgets.PillButton leave = new Widgets.PillButton("Sair da sala", Theme.DANGER, 14);
        leave.addActionListener(e -> core.leaveRoom());
        footer.add(leave);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildWaiting() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(28, 32, 10, 32));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(waitTitle);
        waitStatus.setForeground(Theme.TEXT_SOFT);
        header.add(waitStatus);
        p.add(header, BorderLayout.NORTH);

        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 12));
        card.add(waitCount, BorderLayout.NORTH);
        chips.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(chips, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildGame() {
        JPanel p = new JPanel(new BorderLayout(18, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 24, 4, 24));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(banner, BorderLayout.CENTER);
        p.add(north, BorderLayout.NORTH);
        p.add(stage, BorderLayout.CENTER);

        JPanel east = new JPanel(new BorderLayout(0, 8));
        east.setOpaque(false);
        east.add(position, BorderLayout.NORTH);
        east.add(ranking, BorderLayout.CENTER);
        east.setVisible(false);
        p.add(east, BorderLayout.EAST);
        ranking.putClientProperty("holder", east);

        answerStatus.setForeground(Theme.TEXT_SOFT);
        p.add(answerStatus, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildEnd() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(20, 26, 4, 26));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Theme.centered("Fim de jogo!", 40, true));
        header.add(endPosition);
        p.add(header, BorderLayout.NORTH);
        p.add(podium, BorderLayout.CENTER);
        return p;
    }

    // ---------------------------------------------------------------- atualização

    void update(PlayerSnapshot snapshot) {
        last = snapshot;
        RoomState s = snapshot.state();
        if (s == null || !snapshot.joined() && s.getPhase() == Phase.LOBBY) {
            showWaiting(snapshot);
            return;
        }
        switch (s.getPhase()) {
            case LOBBY -> showWaiting(snapshot);
            case QUESTION, REVEAL -> showGame(snapshot);
            case END -> showEnd(snapshot);
            default -> {
            }
        }
    }

    void tick() {
        stage.tick();
        if (last != null && last.state() != null && last.state().getPhase() == Phase.QUESTION) {
            boolean up = stage.timeIsUp();
            if (up != timeUpShown) {
                update(last); // trava os botões quando o tempo acaba
            }
        }
        JComponent holder = (JComponent) ranking.getClientProperty("holder");
        if (holder != null && holder.isVisible() && ranking.animating()) {
            ranking.repaint();
        }
        if ("end".equals(lastCard)) {
            podium.repaint();
        }
    }

    private void showWaiting(PlayerSnapshot snapshot) {
        waitTitle.setText("Sala de " + snapshot.hostName());
        waitStatus.setText(snapshot.joined()
                ? "Você entrou! Aguardando o apresentador começar…"
                : "Entrando… aguardando a confirmação (ACK) do apresentador");
        List<PlayerScore> players = snapshot.state() == null ? List.of() : snapshot.state().getPlayers();
        int n = players.size();
        waitCount.setText(n + (n == 1 ? " jogador na sala" : " jogadores na sala"));
        List<String> names = new ArrayList<>();
        for (PlayerScore p : players) {
            names.add(p.getName() + (p.getId().equals(snapshot.selfId()) ? " (você)" : ""));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (!names.equals(lastChipNames)) {
            lastChipNames = names;
            chips.removeAll();
            int i = 0;
            for (String name : names) {
                chips.add(new Widgets.Chip(name, Theme.OPTION_COLORS[i++ % 4]));
            }
            chips.revalidate();
            chips.repaint();
        }
        showCard("wait");
    }

    private void showGame(PlayerSnapshot snapshot) {
        RoomState s = snapshot.state();
        boolean reveal = s.getPhase() == Phase.REVEAL;
        stage.show(s, snapshot.myChoice(), true, snapshot.localDeadline());
        timeUpShown = !reveal && snapshot.localDeadline() <= System.currentTimeMillis();
        stage.setInfo(s.getPlayers().size() + " jogadores");

        JComponent holder = (JComponent) ranking.getClientProperty("holder");
        if (reveal) {
            PlayerScore me = s.find(snapshot.selfId());
            banner.showResult(me);
            int pos = s.positionOf(snapshot.selfId());
            position.setText(pos > 0 && me != null
                    ? "Você: " + Theme.ordinal(pos) + " de " + s.getPlayers().size() + " · " + me.getScore() + " pts"
                    : " ");
            ranking.setPlayers(s.getPlayers(), snapshot.selfId(), 5);
            holder.setVisible(true);
            answerStatus.setText("Aguardando o apresentador avançar…");
        } else {
            banner.hideResult();
            holder.setVisible(false);
            if (snapshot.myChoice() < 0) {
                answerStatus.setText(timeUpShown ? "Tempo esgotado!" : "Escolha uma alternativa!");
            } else if (snapshot.answerConfirmed()) {
                answerStatus.setText("✔ Resposta confirmada pelo apresentador (ACK recebido)");
            } else {
                answerStatus.setText("Resposta enviada… aguardando ACK (retransmitindo a cada 500 ms)");
            }
        }
        showCard("game");
    }

    private void showEnd(PlayerSnapshot snapshot) {
        RoomState s = snapshot.state();
        int pos = s.positionOf(snapshot.selfId());
        PlayerScore me = s.find(snapshot.selfId());
        endPosition.setText(pos > 0 && me != null
                ? "Você ficou em " + Theme.ordinal(pos) + " lugar com " + me.getScore() + " pontos"
                : "Partida encerrada");
        podium.setPlayers(s.getPlayers(), snapshot.selfId());
        showCard("end");
    }

    private void showCard(String name) {
        if (!name.equals(lastCard)) {
            lastCard = name;
            cards.show(deck, name);
        }
    }

    /** Faixa colorida com o resultado da pergunta: acertou, errou ou não respondeu. */
    private static final class ResultBanner extends JComponent {
        private String title = "";
        private String detail = "";
        private Color color = Theme.NEUTRAL;

        ResultBanner() {
            setPreferredSize(new Dimension(400, 0));
        }

        void showResult(PlayerScore me) {
            if (me == null || me.getLastResult() == PlayerScore.NO_ANSWER) {
                title = "Sem resposta";
                detail = "O tempo acabou antes da sua resposta chegar";
                color = Theme.NEUTRAL;
            } else if (me.getLastResult() == PlayerScore.RIGHT) {
                title = "Correto!   +" + me.getLastDelta();
                detail = me.getStreak() >= 3
                        ? me.getStreak() + " acertos seguidos: +100 de bônus!"
                        : "Quanto mais rápido, mais pontos";
                color = Theme.ACCENT;
            } else {
                title = "Errou!";
                detail = "A alternativa certa está marcada com ✔";
                color = Theme.DANGER;
            }
            setPreferredSize(new Dimension(400, 84));
            revalidate();
            repaint();
        }

        void hideResult() {
            if (getPreferredSize().height != 0) {
                setPreferredSize(new Dimension(400, 0));
                revalidate();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (getHeight() < 10) {
                return;
            }
            Graphics2D g2 = Widgets.smooth(g);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight() - 6, 20, 20);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(32, true));
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, (getWidth() - tw) / 2, 40);
            g2.setFont(Theme.font(16, false));
            int dw = g2.getFontMetrics().stringWidth(detail);
            g2.drawString(detail, (getWidth() - dw) / 2, 66);
            g2.dispose();
        }
    }
}
