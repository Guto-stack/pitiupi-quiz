package pitiupi.quiz.control;

import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.PlayerScore;
import pitiupi.quiz.model.Question;
import pitiupi.quiz.model.RoomState;
import pitiupi.quiz.net.QuizMessage;
import pitiupi.quiz.net.ReliableChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

/**
 * Apresentador: dono da sala, das perguntas e do placar.
 *
 * <p>Máquina de estados: LOBBY → QUESTION → REVEAL → QUESTION → … → END → (LOBBY | CLOSED).</p>
 *
 * <p>Todos os métodos rodam na thread do protocolo ({@link ReliableChannel#execute}).</p>
 */
public final class HostController {

    private static final long STATE_PERIOD_MS = 1000;
    private static final long LATE_GRACE_MS = 1500;
    private static final int BASE_POINTS = 500;
    private static final int SPEED_POINTS = 500;
    private static final int STREAK_BONUS = 100;

    private final ReliableChannel channel;
    private final String hostName;
    private final QuizUi ui;
    private final String roomId = UUID.randomUUID().toString();
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<String, Answer> answers = new HashMap<>();

    private List<Question> source = List.of();
    private String sourceLabel = "";
    private List<Question> round = List.of();
    /** Respostas certas da rodada, lidas pelos robôs de teste (outra thread). */
    private volatile int[] roundCorrect = new int[0];
    private int questionLimit = 10;
    private int secondsPerQuestion = 20;

    private Phase phase = Phase.LOBBY;
    private int questionIndex = -1;
    private long questionStartedAt;
    private long deadline;
    private ScheduledFuture<?> heartbeat;
    private ScheduledFuture<?> timeout;

    private long version;
    private long delaySum;
    private long delayCount;

    public HostController(ReliableChannel channel, String hostName, QuizUi ui) {
        this.channel = channel;
        this.hostName = hostName;
        this.ui = ui;
    }

    public String roomId() {
        return roomId;
    }

    /** Alternativa certa da pergunta {@code index} da rodada atual, ou -1. Thread-safe. */
    public int correctAnswerOf(int index) {
        int[] answers = roundCorrect;
        return index >= 0 && index < answers.length ? answers[index] : -1;
    }

    public boolean isOpen() {
        return phase != Phase.CLOSED;
    }

    // ---------------------------------------------------------------- comandos da tela

    public void open(List<Question> questions, String label) {
        this.source = List.copyOf(questions);
        this.sourceLabel = label;
        heartbeat = channel.every(this::sendHeartbeat, STATE_PERIOD_MS);
        publish(true);
    }

    public void configure(List<Question> questions, String label, int limit, int seconds) {
        if (phase != Phase.LOBBY) {
            return;
        }
        if (questions != null) {
            this.source = List.copyOf(questions);
            this.sourceLabel = label;
        }
        this.questionLimit = Math.max(1, limit);
        this.secondsPerQuestion = Math.max(5, seconds);
        publish(false);
    }

    public void start() {
        if (phase != Phase.LOBBY && phase != Phase.END) {
            return;
        }
        List<Question> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled);
        round = List.copyOf(shuffled.subList(0, Math.min(questionLimit, shuffled.size())));
        roundCorrect = round.stream().mapToInt(Question::getCorrect).toArray();
        players.values().forEach(Player::reset);
        delaySum = 0;
        delayCount = 0;
        questionIndex = 0;
        beginQuestion();
    }

    /** Encerra o tempo da pergunta atual antes do fim. */
    public void endQuestionNow() {
        if (phase == Phase.QUESTION) {
            reveal();
        }
    }

    public void next() {
        if (phase != Phase.REVEAL) {
            return;
        }
        if (questionIndex + 1 < round.size()) {
            questionIndex++;
            beginQuestion();
        } else {
            phase = Phase.END;
            publish(true);
        }
    }

    public void backToLobby() {
        if (phase != Phase.END) {
            return;
        }
        phase = Phase.LOBBY;
        questionIndex = -1;
        players.values().forEach(Player::reset);
        publish(true);
    }

    public void close() {
        if (phase == Phase.CLOSED) {
            return;
        }
        phase = Phase.CLOSED;
        version++;
        cancel(timeout);
        cancel(heartbeat);
        channel.sendBurst(QuizMessage.state(channel.selfId(), hostName, buildState()), 3, 200);
        ui.onHost(null);
    }

    // ---------------------------------------------------------------- mensagens da rede

    public void onJoin(QuizMessage m) {
        if (phase == Phase.CLOSED) {
            return;
        }
        Player p = players.get(m.getFrom());
        if (p == null) {
            players.put(m.getFrom(), new Player(m.getFrom(), m.getFromName()));
            ui.onNotice(m.getFromName() + " entrou no quiz");
        } else {
            p.name = m.getFromName();
        }
        publish(true);
    }

    public void onLeave(QuizMessage m) {
        if (players.remove(m.getFrom()) != null) {
            ui.onNotice(m.getFromName() + " saiu do quiz");
            answers.remove(m.getFrom());
            if (phase == Phase.QUESTION && everyoneAnswered()) {
                reveal();
            } else {
                publish(true);
            }
        }
    }

    public void onAnswer(QuizMessage m) {
        if (phase != Phase.QUESTION || m.getQuestionIndex() != questionIndex) {
            return; // resposta atrasada de outra pergunta: o ACK já foi, mas não vale ponto
        }
        if (m.getChoice() < 0 || m.getChoice() > 3) {
            return;
        }
        long hostElapsed = System.currentTimeMillis() - questionStartedAt;
        long limit = secondsPerQuestion * 1000L;
        if (hostElapsed > limit + LATE_GRACE_MS) {
            return;
        }
        // JOIN perdido não impede de jogar: quem responde entra no placar.
        players.computeIfAbsent(m.getFrom(), id -> new Player(id, m.getFromName()));
        if (answers.containsKey(m.getFrom())) {
            return;
        }
        long playerElapsed = Math.max(0, Math.min(limit, m.getElapsedMillis()));
        answers.put(m.getFrom(), new Answer(m.getChoice(), playerElapsed));

        // O apresentador mede do envio da pergunta até a resposta chegar; o jogador mede só o tempo
        // de pensar. A diferença é o caminho de ida (pergunta) + volta (resposta) pela rede.
        long networkDelay = hostElapsed - playerElapsed;
        if (networkDelay >= 0) {
            delaySum += networkDelay;
            delayCount++;
        }

        if (everyoneAnswered()) {
            reveal();
        } else {
            publish(true);
        }
    }

    // ---------------------------------------------------------------- lógica interna

    private void beginQuestion() {
        answers.clear();
        phase = Phase.QUESTION;
        questionStartedAt = System.currentTimeMillis();
        deadline = questionStartedAt + secondsPerQuestion * 1000L;
        cancel(timeout);
        timeout = channel.schedule(this::reveal, secondsPerQuestion * 1000L);
        publish(true);
    }

    private void reveal() {
        if (phase != Phase.QUESTION) {
            return;
        }
        cancel(timeout);
        Question q = round.get(questionIndex);
        long limit = secondsPerQuestion * 1000L;
        for (Player p : players.values()) {
            Answer a = answers.get(p.id);
            if (a == null) {
                p.lastResult = PlayerScore.NO_ANSWER;
                p.lastDelta = 0;
                p.streak = 0;
            } else if (a.choice == q.getCorrect()) {
                p.streak++;
                double speed = 1.0 - (double) a.elapsed / limit;
                int points = BASE_POINTS + (int) Math.round(SPEED_POINTS * speed);
                if (p.streak >= 3) {
                    points += STREAK_BONUS;
                }
                p.score += points;
                p.lastDelta = points;
                p.lastResult = PlayerScore.RIGHT;
            } else {
                p.lastResult = PlayerScore.WRONG;
                p.lastDelta = 0;
                p.streak = 0;
            }
        }
        phase = Phase.REVEAL;
        publish(true);
    }

    private boolean everyoneAnswered() {
        return !players.isEmpty() && answers.keySet().containsAll(players.keySet());
    }

    private void sendHeartbeat() {
        if (phase == Phase.CLOSED) {
            return;
        }
        channel.send(QuizMessage.state(channel.selfId(), hostName, buildState()));
        ui.onHost(snapshot());
    }

    /** Mudança de estado: 3 cópias do retrato (UDP pode perder uma) e atualiza a tela. */
    private void publish(boolean network) {
        version++;
        RoomState state = buildState();
        if (network) {
            channel.sendBurst(QuizMessage.state(channel.selfId(), hostName, state), 3, 150);
        }
        ui.onHost(snapshot(state));
    }

    private HostSnapshot snapshot() {
        return snapshot(buildState());
    }

    private HostSnapshot snapshot(RoomState state) {
        double avgDelay = delayCount == 0 ? -1 : (double) delaySum / delayCount;
        return new HostSnapshot(state, deadline, sourceLabel, source.size(), avgDelay);
    }

    private RoomState buildState() {
        boolean inGame = phase == Phase.QUESTION || phase == Phase.REVEAL;
        Question q = inGame ? round.get(questionIndex) : null;
        int[] votes = null;
        if (phase == Phase.REVEAL) {
            votes = new int[4];
            for (Answer a : answers.values()) {
                votes[a.choice]++;
            }
        }
        List<PlayerScore> ranking = new ArrayList<>();
        for (Player p : players.values()) {
            ranking.add(new PlayerScore(p.id, p.name, p.score, p.lastDelta, p.lastResult, p.streak));
        }
        ranking.sort(Comparator.comparingInt(PlayerScore::getScore).reversed()
                .thenComparing(PlayerScore::getName, String.CASE_INSENSITIVE_ORDER));
        long remaining = phase == Phase.QUESTION ? Math.max(0, deadline - System.currentTimeMillis()) : 0;
        int count = (phase == Phase.LOBBY) ? Math.min(questionLimit, source.size()) : round.size();
        return new RoomState(
                roomId,
                version,
                channel.selfId(),
                hostName,
                phase,
                questionIndex,
                count,
                q == null ? null : q.getText(),
                q == null ? null : q.getOptions(),
                secondsPerQuestion,
                remaining,
                phase == Phase.REVEAL ? q.getCorrect() : -1,
                votes,
                answers.size(),
                ranking
        );
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static final class Player {
        final String id;
        String name;
        int score;
        int lastDelta;
        int lastResult = PlayerScore.NO_ANSWER;
        int streak;

        Player(String id, String name) {
            this.id = id;
            this.name = name;
        }

        void reset() {
            score = 0;
            lastDelta = 0;
            lastResult = PlayerScore.NO_ANSWER;
            streak = 0;
        }
    }

    private record Answer(int choice, long elapsed) {
    }
}
