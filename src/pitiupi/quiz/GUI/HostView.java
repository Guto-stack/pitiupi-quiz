package pitiupi.quiz.GUI;

import pitiupi.quiz.control.HostSnapshot;
import pitiupi.quiz.control.QuizCore;
import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.PlayerScore;
import pitiupi.quiz.model.Question;
import pitiupi.quiz.model.QuestionBank;
import pitiupi.quiz.model.RoomState;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tela do apresentador (a que vai no projetor): lobby, perguntas com revelação e pódio.
 */
final class HostView extends JPanel {

    private static final int[] SECONDS = {10, 15, 20, 30};
    private static final int[] LIMITS = {5, 10, 15, 20, Integer.MAX_VALUE};

    private final QuizCore core;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    // lobby
    private final JLabel lobbyTitle = Theme.centered(" ", 36, true);
    private final JLabel playerCount = Theme.centered(" ", 22, true);
    private final JPanel chips = new JPanel(new GridLayout(0, 5, 10, 10));
    private final JComboBox<String> secondsBox = new JComboBox<>();
    private final JComboBox<String> limitBox = new JComboBox<>();
    private final JLabel sourceLabel = Theme.label("", 15, false);
    private final Widgets.PillButton startButton = new Widgets.PillButton("Começar  ▶", Theme.ACCENT, 22);

    // jogo
    private final GameStage stage = new GameStage(i -> { });
    private final RankingPanel ranking = new RankingPanel();
    private final Widgets.PillButton endNow = new Widgets.PillButton("Encerrar tempo", Theme.NEUTRAL, 18);
    private final Widgets.PillButton next = new Widgets.PillButton("Próxima pergunta  ▶", Theme.ACCENT, 20);

    // fim
    private final PodiumPanel podium = new PodiumPanel();

    private List<Question> questions = QuestionBank.builtIn();
    private String questionsLabel = "Embutidas: Redes de Computadores";
    private String lastCard = "";
    private List<String> lastChipNames = List.of();
    private HostSnapshot last;

    HostView(QuizCore core) {
        super(new BorderLayout());
        this.core = core;
        setOpaque(false);
        deck.setOpaque(false);
        deck.add(buildLobby(), "lobby");
        deck.add(buildGame(), "game");
        deck.add(buildEnd(), "end");
        add(deck, BorderLayout.CENTER);
    }

    /** Valores de configuração para a criação da sala. */
    List<Question> questions() {
        return questions;
    }

    String questionsLabel() {
        return questionsLabel;
    }

    int limit() {
        return LIMITS[Math.max(0, limitBox.getSelectedIndex())];
    }

    int seconds() {
        return SECONDS[Math.max(0, secondsBox.getSelectedIndex())];
    }

    // ---------------------------------------------------------------- construção

    private JPanel buildLobby() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(22, 32, 18, 32));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(lobbyTitle);
        JLabel hint = Theme.centered("Abram “Quiz ao vivo” no Pitiupi e cliquem em Entrar", 19, false);
        hint.setForeground(Theme.TEXT_SOFT);
        header.add(hint);
        p.add(header, BorderLayout.NORTH);

        Widgets.Card center = new Widgets.Card(new BorderLayout(0, 12));
        center.add(playerCount, BorderLayout.NORTH);
        chips.setOpaque(false);
        JPanel chipsHolder = new JPanel(new BorderLayout());
        chipsHolder.setOpaque(false);
        chipsHolder.add(chips, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(chipsHolder);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        center.add(scroll, BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 10));
        bottom.setOpaque(false);
        JPanel config = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        config.setOpaque(false);
        for (int s : SECONDS) {
            secondsBox.addItem(s + " s por pergunta");
        }
        secondsBox.setSelectedIndex(2);
        for (int l : LIMITS) {
            limitBox.addItem(l == Integer.MAX_VALUE ? "Todas as perguntas" : l + " perguntas");
        }
        limitBox.setSelectedIndex(1);
        secondsBox.addActionListener(e -> pushConfig(false));
        limitBox.addActionListener(e -> pushConfig(false));
        Widgets.PillButton load = new Widgets.PillButton("Carregar .txt", Theme.NEUTRAL, 15);
        load.addActionListener(e -> loadFile());
        sourceLabel.setForeground(Theme.TEXT_SOFT);
        config.add(secondsBox);
        config.add(limitBox);
        config.add(load);
        JPanel configRows = new JPanel(new BorderLayout(0, 6));
        configRows.setOpaque(false);
        configRows.add(config, BorderLayout.NORTH);
        sourceLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        configRows.add(sourceLabel, BorderLayout.SOUTH);
        bottom.add(configRows, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        Widgets.PillButton close = new Widgets.PillButton("Fechar sala", Theme.DANGER, 16);
        close.addActionListener(e -> confirmClose());
        Widgets.PillButton bot = new Widgets.PillButton("+ Robô de teste", Theme.NEUTRAL, 16);
        bot.setToolTipText("Jogador automático com id próprio: os datagramas dele saem pelo socket multicast e voltam pelo loopback");
        bot.addActionListener(e -> core.addBot());
        startButton.addActionListener(e -> core.startGame());
        actions.add(close);
        actions.add(bot);
        actions.add(startButton);
        bottom.add(actions, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        updateSourceLabel();
        return p;
    }

    private JPanel buildGame() {
        JPanel p = new JPanel(new BorderLayout(18, 12));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(18, 26, 14, 26));
        p.add(stage, BorderLayout.CENTER);

        ranking.setVisible(false);
        p.add(ranking, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        Widgets.PillButton close = new Widgets.PillButton("Fechar sala", Theme.DANGER, 15);
        close.addActionListener(e -> confirmClose());
        endNow.addActionListener(e -> core.endQuestionNow());
        next.addActionListener(e -> core.nextStep());
        actions.add(close);
        actions.add(endNow);
        actions.add(next);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildEnd() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(20, 26, 14, 26));
        p.add(Theme.centered("Fim de jogo!", 40, true), BorderLayout.NORTH);
        p.add(podium, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        actions.setOpaque(false);
        Widgets.PillButton again = new Widgets.PillButton("Jogar de novo", Theme.OPTION_COLORS[1], 20);
        again.addActionListener(e -> core.backToLobby());
        Widgets.PillButton close = new Widgets.PillButton("Fechar sala", Theme.DANGER, 20);
        close.addActionListener(e -> core.closeRoom());
        actions.add(again);
        actions.add(close);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    // ---------------------------------------------------------------- atualização

    void update(HostSnapshot snapshot) {
        last = snapshot;
        RoomState s = snapshot.state();
        switch (s.getPhase()) {
            case LOBBY -> showLobby(s);
            case QUESTION, REVEAL -> showGame(snapshot);
            case END -> {
                podium.setPlayers(s.getPlayers(), null);
                showCard("end");
            }
            default -> {
            }
        }
    }

    void tick() {
        stage.tick();
        if (ranking.isVisible() && ranking.animating()) {
            ranking.repaint();
        }
        if ("end".equals(lastCard)) {
            podium.repaint();
        }
    }

    double avgAnswerDelay() {
        return last == null ? -1 : last.avgAnswerDelayMs();
    }

    private void showLobby(RoomState s) {
        lobbyTitle.setText("Sala de " + s.getHostName());
        int n = s.getPlayers().size();
        playerCount.setText(n == 0 ? "Aguardando jogadores…" : n + (n == 1 ? " jogador na sala" : " jogadores na sala"));
        List<String> names = new ArrayList<>();
        for (PlayerScore p : s.getPlayers()) {
            names.add(p.getName());
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
        startButton.setEnabled(n > 0);
        startButton.setToolTipText(n > 0 ? null : "Espere pelo menos um jogador entrar");
        showCard("lobby");
    }

    private void showGame(HostSnapshot snapshot) {
        RoomState s = snapshot.state();
        boolean reveal = s.getPhase() == Phase.REVEAL;
        stage.show(s, -1, false, snapshot.localDeadline());
        int total = s.getPlayers().size();
        stage.setInfo(reveal
                ? acertos(s)
                : s.getAnsweredCount() + " de " + total + (total == 1 ? " respondeu" : " responderam"));
        ranking.setVisible(reveal);
        if (reveal) {
            ranking.setPlayers(s.getPlayers(), null, 5);
        }
        endNow.setVisible(!reveal);
        next.setVisible(reveal);
        next.setText(s.getQuestionIndex() + 1 < s.getQuestionCount() ? "Próxima pergunta  ▶" : "Ver pódio  ▶");
        showCard("game");
    }

    private static String acertos(RoomState s) {
        int right = 0;
        for (PlayerScore p : s.getPlayers()) {
            if (p.getLastResult() == PlayerScore.RIGHT) {
                right++;
            }
        }
        return right + " de " + s.getPlayers().size() + (right == 1 ? " acertou" : " acertaram");
    }

    private void showCard(String name) {
        if (!name.equals(lastCard)) {
            lastCard = name;
            cards.show(deck, name);
        }
    }

    private void pushConfig(boolean withQuestions) {
        if (last != null && last.state().getPhase() == Phase.LOBBY) {
            core.configureRoom(withQuestions ? questions : null, questionsLabel, limit(), seconds());
        }
    }

    private void loadFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Arquivo de perguntas (enunciado | A | B | C | D | número da certa)");
        chooser.setFileFilter(new FileNameExtensionFilter("Texto (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            questions = QuestionBank.fromFile(chooser.getSelectedFile().toPath());
            questionsLabel = "Arquivo: " + chooser.getSelectedFile().getName();
            updateSourceLabel();
            pushConfig(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Arquivo de perguntas inválido", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSourceLabel() {
        sourceLabel.setText(questionsLabel + " (" + questions.size() + ")");
    }

    private void confirmClose() {
        int answer = JOptionPane.showConfirmDialog(this, "Encerrar a sala para todos os jogadores?", "Fechar sala",
                JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            core.closeRoom();
        }
    }
}
