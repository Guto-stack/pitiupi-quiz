package pitiupi.quiz.GUI;

import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.RoomState;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.IntConsumer;

/**
 * Palco de uma pergunta: progresso, cronômetro, enunciado e as quatro alternativas.
 * Usado pelo apresentador (só exibe) e pelo jogador (clica).
 */
final class GameStage extends JPanel {

    private final JLabel progress = Theme.label("", 20, true);
    private final JLabel info = Theme.label("", 20, true);
    private final Widgets.CountdownBar bar = new Widgets.CountdownBar();
    private final WrapText question = new WrapText(36, true, true);
    private final OptionTile[] tiles = new OptionTile[4];

    GameStage(IntConsumer onClick) {
        super(new BorderLayout(0, 16));
        setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        labels.add(progress, BorderLayout.WEST);
        labels.add(info, BorderLayout.EAST);
        top.add(labels, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        question.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        question.setPreferredSize(new Dimension(600, 120));
        add(question, BorderLayout.CENTER);

        JPanel grid = new JPanel(new GridLayout(2, 2, 14, 14));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(600, 250));
        for (int i = 0; i < 4; i++) {
            tiles[i] = new OptionTile(i);
            tiles[i].setOnClick(onClick);
            grid.add(tiles[i]);
        }
        add(grid, BorderLayout.SOUTH);
    }

    /**
     * Atualiza o palco com o retrato da sala.
     *
     * @param myChoice     escolha do jogador (-1 nenhuma; no apresentador sempre -1)
     * @param canAnswer    as alternativas aceitam clique
     * @param localDeadline instante local em que a pergunta encerra
     */
    void show(RoomState s, int myChoice, boolean canAnswer, long localDeadline) {
        progress.setText("Pergunta " + (s.getQuestionIndex() + 1) + " de " + s.getQuestionCount());
        question.setText(s.getQuestionText());

        if (s.getPhase() == Phase.QUESTION) {
            bar.start(localDeadline, s.getSecondsPerQuestion() * 1000L);
        } else {
            bar.freeze();
        }

        String[] options = s.getOptions();
        int[] votes = s.getVotes();
        int maxVotes = 1;
        for (int v : votes) {
            maxVotes = Math.max(maxVotes, v);
        }
        boolean reveal = s.getPhase() == Phase.REVEAL;
        boolean timeUp = !reveal && localDeadline <= System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            String text = options == null ? "" : options[i];
            OptionTile.Mode mode;
            if (reveal) {
                mode = i == s.getCorrect() ? OptionTile.Mode.CORRECT
                        : i == myChoice ? OptionTile.Mode.CHOSEN : OptionTile.Mode.DIMMED;
            } else if (myChoice >= 0) {
                mode = i == myChoice ? OptionTile.Mode.CHOSEN : OptionTile.Mode.DIMMED;
            } else if (canAnswer && timeUp) {
                mode = OptionTile.Mode.DIMMED;
            } else {
                mode = OptionTile.Mode.NORMAL;
            }
            boolean clickable = canAnswer && !reveal && myChoice < 0 && !timeUp;
            tiles[i].show(text, mode, clickable, reveal ? votes[i] : -1, maxVotes);
        }
    }

    void setInfo(String text) {
        info.setText(text);
    }

    boolean timeIsUp() {
        return bar.remaining() <= 0;
    }

    void tick() {
        bar.repaint();
    }
}
