package pitiupi.quiz.GUI;

import pitiupi.quiz.control.HostSnapshot;
import pitiupi.quiz.control.PlayerSnapshot;
import pitiupi.quiz.control.QuizCore;
import pitiupi.quiz.control.QuizUi;
import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.RoomState;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Painel registrado na shell do Pitiupi. Troca entre início, apresentador e jogador,
 * e implementa {@link QuizUi}: tudo que chega da thread do protocolo é repassado para a EDT.
 */
public final class QuizPanel extends Widgets.GradientPanel implements QuizUi {

    private final Supplier<String> userName;
    private final Runnable openTab;
    private final Consumer<String> status;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    private QuizCore core;
    private HomeView home;
    private HostView host;
    private PlayerView player;
    private NetStatsBar stats;

    private boolean hosting;
    private boolean playing;
    private int lastQuestionSeen = -1;
    private int ticks;

    public QuizPanel(Supplier<String> userName, Runnable openTab, Consumer<String> status) {
        super(new BorderLayout());
        this.userName = userName;
        this.openTab = openTab;
        this.status = status;
    }

    /** Monta as telas; separado do construtor porque o {@link QuizCore} precisa do painel como UI. */
    public void attach(QuizCore core) {
        this.core = core;
        home = new HomeView(userName, this::createRoom, core::joinRoom);
        host = new HostView(core);
        player = new PlayerView(core);
        stats = new NetStatsBar(core::setSimulatedLossRate);

        deck.setOpaque(false);
        deck.add(home, "home");
        deck.add(host, "host");
        deck.add(player, "player");
        add(deck, BorderLayout.CENTER);
        add(stats, BorderLayout.SOUTH);

        // ~20 quadros por segundo para cronômetro e animações; estatísticas a cada 0,5 s.
        Timer timer = new Timer(50, e -> tick());
        timer.start();
    }

    private void createRoom() {
        core.openRoom(host.questions(), host.questionsLabel(), host.limit(), host.seconds());
    }

    private void tick() {
        if (hosting) {
            host.tick();
        } else if (playing) {
            player.tick();
        }
        if (++ticks % 10 == 0) {
            stats.refresh(core.stats().snapshot(), hosting ? host.avgAnswerDelay() : -1);
            if (!hosting && !playing) {
                home.refreshName();
            }
        }
    }

    private void showCurrent() {
        cards.show(deck, hosting ? "host" : playing ? "player" : "home");
    }

    // ---------------------------------------------------------------- QuizUi (thread do protocolo)

    @Override
    public void onRooms(List<RoomState> rooms) {
        SwingUtilities.invokeLater(() -> home.setRooms(rooms));
    }

    @Override
    public void onHost(HostSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            hosting = snapshot != null;
            if (snapshot != null) {
                host.update(snapshot);
            }
            showCurrent();
        });
    }

    @Override
    public void onPlayer(PlayerSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            playing = snapshot != null;
            if (snapshot != null) {
                player.update(snapshot);
                RoomState s = snapshot.state();
                // Pergunta nova: traz a aba do Quiz para a frente, mesmo que a pessoa esteja no chat.
                if (s != null && s.getPhase() == Phase.QUESTION && s.getQuestionIndex() != lastQuestionSeen) {
                    lastQuestionSeen = s.getQuestionIndex();
                    openTab.run();
                } else if (s != null && s.getPhase() == Phase.LOBBY) {
                    lastQuestionSeen = -1;
                }
            } else {
                lastQuestionSeen = -1;
            }
            showCurrent();
        });
    }

    @Override
    public void onNotice(String text) {
        status.accept(text);
    }
}
