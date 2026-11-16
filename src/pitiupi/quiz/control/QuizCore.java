package pitiupi.quiz.control;

import pitiupi.control.ConnectionManager;
import pitiupi.quiz.model.Question;
import pitiupi.quiz.net.NetStats;
import pitiupi.quiz.net.QuizMessage;
import pitiupi.quiz.net.ReliableChannel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Ponto de entrada da lógica: liga o canal, o apresentador e o jogador.
 *
 * <p>Os métodos públicos podem ser chamados de qualquer thread (Swing ou rede): todos
 * repassam o trabalho para a thread única do protocolo.</p>
 */
public final class QuizCore {

    private static final int MAX_BOTS = 8;
    private static final String[] BOT_NAMES = {"Alfa", "Bravo", "Charlie", "Delta", "Eco", "Foxtrot", "Golf", "Hotel"};

    private final ConnectionManager connectionManager;
    private final ReliableChannel channel;
    private final QuizUi ui;
    private final PlayerController player;
    private HostController host;
    private final List<BotPlayer> bots = new CopyOnWriteArrayList<>();

    public QuizCore(ConnectionManager connectionManager, QuizUi ui) {
        this.connectionManager = connectionManager;
        this.ui = ui;
        this.channel = new ReliableChannel(connectionManager);
        this.player = new PlayerController(channel, ui, connectionManager.getUserName());
        channel.setHandler(this::route);
    }

    public NetStats stats() {
        return channel.stats();
    }

    public void setSimulatedLossRate(double rate) {
        channel.setSimulatedLossRate(rate);
    }

    public void onDatagram(QuizMessage message) {
        channel.onDatagram(message);
        for (BotPlayer bot : bots) {
            bot.onDatagram(message);
        }
    }

    // ---------------------------------------------------------------- apresentador

    public void openRoom(List<Question> questions, String label, int limit, int seconds) {
        channel.execute(() -> {
            if (host != null && host.isOpen()) {
                return;
            }
            player.leave();
            host = new HostController(channel, connectionManager.getUserName(), ui);
            host.configure(null, null, limit, seconds);
            host.open(questions, label);
        });
    }

    public void configureRoom(List<Question> questions, String label, int limit, int seconds) {
        withHost(h -> h.configure(questions, label, limit, seconds));
    }

    public void startGame() {
        withHost(HostController::start);
    }

    public void endQuestionNow() {
        withHost(HostController::endQuestionNow);
    }

    public void nextStep() {
        withHost(HostController::next);
    }

    public void backToLobby() {
        withHost(HostController::backToLobby);
    }

    public void closeRoom() {
        withHost(h -> {
            h.close();
            host = null;
            removeBots();
        });
    }

    /** Põe um robô de teste na minha sala (máximo {@value #MAX_BOTS}). */
    public void addBot() {
        withHost(h -> {
            if (!h.isOpen()) {
                return;
            }
            if (bots.size() >= MAX_BOTS) {
                ui.onNotice("Limite de " + MAX_BOTS + " robôs");
                return;
            }
            String name = "Robô " + BOT_NAMES[bots.size() % BOT_NAMES.length];
            HostController current = h;
            bots.add(new BotPlayer(connectionManager, name, h.roomId(), current::correctAnswerOf));
        });
    }

    private void removeBots() {
        for (BotPlayer bot : bots) {
            bot.shutdown();
        }
        bots.clear();
    }

    // ---------------------------------------------------------------- jogador

    public void joinRoom(String roomId) {
        channel.execute(() -> {
            if (host != null && host.isOpen()) {
                ui.onNotice("Feche a sua sala antes de entrar em outra");
                return;
            }
            player.setUserName(connectionManager.getUserName());
            player.join(roomId);
        });
    }

    public void answer(int choice) {
        channel.execute(() -> player.answer(choice));
    }

    public void leaveRoom() {
        channel.execute(player::leave);
    }

    public void shutdown() {
        channel.execute(() -> {
            if (host != null) {
                host.close();
            }
            removeBots();
            player.leave();
        });
        channel.schedule(channel::shutdown, 800);
    }

    // ---------------------------------------------------------------- roteamento (thread do protocolo)

    private void route(QuizMessage m) {
        switch (m.getType()) {
            case STATE -> player.onState(m);
            case JOIN -> forHost(m, HostController::onJoin);
            case ANSWER -> forHost(m, HostController::onAnswer);
            case LEAVE -> forHost(m, HostController::onLeave);
            default -> {
            }
        }
    }

    private void forHost(QuizMessage m, java.util.function.BiConsumer<HostController, QuizMessage> action) {
        if (host != null && host.isOpen() && host.roomId().equals(m.getRoomId())) {
            action.accept(host, m);
        }
    }

    private void withHost(Consumer<HostController> action) {
        channel.execute(() -> {
            if (host != null) {
                action.accept(host);
            }
        });
    }
}
