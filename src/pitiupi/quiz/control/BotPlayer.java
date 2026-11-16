package pitiupi.quiz.control;

import pitiupi.control.ConnectionManager;
import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.RoomState;
import pitiupi.quiz.net.QuizMessage;
import pitiupi.quiz.net.ReliableChannel;

import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

/**
 * Robô de teste: um jogador completo que roda dentro da janela do apresentador.
 *
 * <p>Tem o próprio {@link ReliableChannel} (id, ACKs e retransmissões próprios) e usa o mesmo
 * {@link PlayerController} de um jogador humano. Os datagramas saem pelo socket multicast real e
 * voltam pelo loopback, ou seja, passam pela pilha de rede do sistema operacional.</p>
 *
 * <p>Serve para demonstrar o Quiz num único computador: no Linux, duas janelas do Pitiupi não
 * conseguem abrir a mesma porta TCP, então não dá para rodar dois peers na mesma máquina.</p>
 */
final class BotPlayer implements QuizUi {

    private final ReliableChannel channel;
    private final PlayerController player;
    private final String roomId;
    private final IntUnaryOperator correctAnswerOf;
    private final Random random = new Random();
    private final double skill;
    private boolean joinRequested;
    private int answeredQuestion = -1;

    BotPlayer(ConnectionManager connectionManager, String name, String roomId, IntUnaryOperator correctAnswerOf) {
        this.channel = new ReliableChannel(connectionManager);
        this.player = new PlayerController(channel, this, name);
        this.roomId = roomId;
        this.correctAnswerOf = correctAnswerOf;
        this.skill = 0.45 + random.nextDouble() * 0.4;
        channel.setHandler(m -> {
            if (m.getType() == QuizMessage.Type.STATE) {
                player.onState(m);
            }
        });
    }

    void onDatagram(QuizMessage message) {
        channel.onDatagram(message);
    }

    void shutdown() {
        channel.execute(player::leave);
        channel.schedule(channel::shutdown, 600);
    }

    @Override
    public void onRooms(List<RoomState> rooms) {
        if (joinRequested) {
            return;
        }
        for (RoomState r : rooms) {
            if (r.getRoomId().equals(roomId)) {
                joinRequested = true;
                player.join(roomId);
                return;
            }
        }
    }

    @Override
    public void onPlayer(PlayerSnapshot snapshot) {
        if (snapshot == null || snapshot.state() == null) {
            return;
        }
        RoomState s = snapshot.state();
        if (s.getPhase() == Phase.LOBBY) {
            answeredQuestion = -1;
        }
        if (s.getPhase() != Phase.QUESTION || s.getQuestionIndex() == answeredQuestion) {
            return;
        }
        int question = s.getQuestionIndex();
        answeredQuestion = question;
        long window = (long) (s.getSecondsPerQuestion() * 1000L * 0.6);
        long delay = 1200 + (long) (random.nextDouble() * window);
        channel.schedule(() -> {
            int correct = correctAnswerOf.applyAsInt(question);
            int choice = (correct >= 0 && random.nextDouble() < skill) ? correct : random.nextInt(4);
            player.answer(choice);
        }, delay);
    }

    @Override
    public void onHost(HostSnapshot snapshot) {
        // robô nunca apresenta
    }

    @Override
    public void onNotice(String text) {
        // robô não lê avisos
    }
}
