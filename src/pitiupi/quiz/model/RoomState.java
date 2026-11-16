package pitiupi.quiz.model;

import java.io.Serializable;
import java.util.List;

/**
 * Retrato completo da sala, montado pelo apresentador.
 *
 * <p>Viaja dentro de {@code QuizMessage.STATE}, por multicast, a cada segundo e sempre que algo muda.
 * Como cada retrato descreve a sala inteira, perder um datagrama não deixa ninguém num estado
 * errado: o próximo retrato corrige. Quem entra no meio da partida também se sincroniza assim.</p>
 *
 * <p>{@code remainingMillis} é relativo: o apresentador manda quanto falta, e o jogador soma ao
 * próprio relógio. Assim os relógios das máquinas não precisam estar sincronizados.</p>
 */
public final class RoomState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String roomId;
    private final long version;
    private final String hostId;
    private final String hostName;
    private final Phase phase;
    private final int questionIndex;
    private final int questionCount;
    private final String questionText;
    private final String[] options;
    private final int secondsPerQuestion;
    private final long remainingMillis;
    private final int correct;
    private final int[] votes;
    private final int answeredCount;
    private final List<PlayerScore> players;

    public RoomState(
            String roomId,
            long version,
            String hostId,
            String hostName,
            Phase phase,
            int questionIndex,
            int questionCount,
            String questionText,
            String[] options,
            int secondsPerQuestion,
            long remainingMillis,
            int correct,
            int[] votes,
            int answeredCount,
            List<PlayerScore> players
    ) {
        this.roomId = roomId;
        this.version = version;
        this.hostId = hostId;
        this.hostName = hostName;
        this.phase = phase;
        this.questionIndex = questionIndex;
        this.questionCount = questionCount;
        this.questionText = questionText;
        this.options = options;
        this.secondsPerQuestion = secondsPerQuestion;
        this.remainingMillis = remainingMillis;
        this.correct = correct;
        this.votes = votes;
        this.answeredCount = answeredCount;
        this.players = List.copyOf(players);
    }

    public String getRoomId() {
        return roomId;
    }

    /**
     * Cresce a cada mudança de estado. Cópias atrasadas de um retrato antigo chegam com versão
     * menor e são descartadas: sem isso, a tela do jogador "voltaria no tempo".
     */
    public long getVersion() {
        return version;
    }

    public String getHostId() {
        return hostId;
    }

    public String getHostName() {
        return hostName;
    }

    public Phase getPhase() {
        return phase;
    }

    /** Índice da pergunta atual, começando em 0. -1 no lobby. */
    public int getQuestionIndex() {
        return questionIndex;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String[] getOptions() {
        return options == null ? null : options.clone();
    }

    public int getSecondsPerQuestion() {
        return secondsPerQuestion;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    /** Alternativa correta (0-3). Só é preenchida na fase REVEAL; antes vale -1 para ninguém espiar. */
    public int getCorrect() {
        return correct;
    }

    public int[] getVotes() {
        return votes == null ? new int[4] : votes.clone();
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    /** Jogadores ordenados do maior para o menor placar. */
    public List<PlayerScore> getPlayers() {
        return players;
    }

    public int positionOf(String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(playerId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public PlayerScore find(String playerId) {
        for (PlayerScore p : players) {
            if (p.getId().equals(playerId)) {
                return p;
            }
        }
        return null;
    }
}
