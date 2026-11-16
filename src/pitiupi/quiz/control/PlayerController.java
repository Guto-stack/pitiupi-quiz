package pitiupi.quiz.control;

import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.RoomState;
import pitiupi.quiz.net.QuizMessage;
import pitiupi.quiz.net.ReliableChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lado do jogador: descobre salas pelos retratos {@code STATE}, entra, responde e espelha o placar.
 *
 * <p>Todos os métodos rodam na thread do protocolo ({@link ReliableChannel#execute}).</p>
 */
public final class PlayerController {

    private static final long ROOM_EXPIRY_MS = 5000;
    private static final long JOIN_GIVE_UP_MS = 8000;
    private static final long ANSWER_GIVE_UP_MS = 8000;

    private final ReliableChannel channel;
    private final QuizUi ui;
    private final Map<String, SeenRoom> rooms = new HashMap<>();
    /** Salas que já fecharam: cópias atrasadas de retratos antigos delas são ignoradas. */
    private final Set<String> closedRooms = new HashSet<>();

    private String userName;
    private String roomId;
    private String hostId;
    private String hostName;
    private boolean joined;
    private RoomState current;
    private int seenQuestionIndex = -1;
    private long questionSeenAt;
    private long localDeadline;
    private int myChoice = -1;
    private boolean answerConfirmed;

    public PlayerController(ReliableChannel channel, QuizUi ui, String userName) {
        this.channel = channel;
        this.ui = ui;
        this.userName = userName;
        channel.every(this::expireRooms, 1000);
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean inRoom() {
        return roomId != null;
    }

    // ---------------------------------------------------------------- comandos da tela

    public void join(String targetRoomId) {
        SeenRoom room = rooms.get(targetRoomId);
        if (room == null) {
            ui.onNotice("A sala não está mais disponível");
            return;
        }
        if (roomId != null) {
            leave();
        }
        roomId = targetRoomId;
        hostId = room.state.getHostId();
        hostName = room.state.getHostName();
        joined = false;
        current = null;
        seenQuestionIndex = -1;
        myChoice = -1;
        String joiningRoom = roomId;
        channel.sendReliable(
                QuizMessage.join(channel.selfId(), userName, hostId, roomId),
                JOIN_GIVE_UP_MS,
                () -> {
                    if (joiningRoom.equals(roomId)) {
                        joined = true;
                        publish();
                    }
                },
                () -> {
                    if (joiningRoom.equals(roomId) && !joined) {
                        dropRoom("O apresentador não respondeu ao pedido de entrada");
                    }
                }
        );
        // Mostra o estado que já conhecemos enquanto o JOIN viaja.
        applyState(room.state);
    }

    public void answer(int choice) {
        if (current == null || current.getPhase() != Phase.QUESTION || myChoice != -1) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > localDeadline) {
            return;
        }
        myChoice = choice;
        answerConfirmed = false;
        int question = current.getQuestionIndex();
        channel.sendReliable(
                QuizMessage.answer(channel.selfId(), userName, hostId, roomId, question, choice, now - questionSeenAt),
                ANSWER_GIVE_UP_MS,
                () -> {
                    if (current != null && current.getQuestionIndex() == question) {
                        answerConfirmed = true;
                        publish();
                    }
                },
                () -> ui.onNotice("A resposta não foi confirmada pelo apresentador")
        );
        publish();
    }

    public void leave() {
        if (roomId == null) {
            return;
        }
        channel.send(QuizMessage.leave(channel.selfId(), userName, hostId, roomId));
        channel.cancelPendingTo(hostId);
        roomId = null;
        hostId = null;
        current = null;
        ui.onPlayer(null);
        publishRooms();
    }

    // ---------------------------------------------------------------- rede

    public void onState(QuizMessage m) {
        RoomState state = m.getState();
        if (state == null) {
            return;
        }
        if (closedRooms.contains(state.getRoomId())) {
            return;
        }
        SeenRoom known = rooms.get(state.getRoomId());
        if (known != null && state.getVersion() < known.state.getVersion()) {
            return; // cópia atrasada de um retrato antigo
        }
        boolean isNew = known == null;
        if (state.getPhase() == Phase.CLOSED) {
            rooms.remove(state.getRoomId());
            closedRooms.add(state.getRoomId());
        } else {
            rooms.put(state.getRoomId(), new SeenRoom(state, System.currentTimeMillis()));
        }
        if (isNew && state.getPhase() != Phase.CLOSED && roomId == null) {
            ui.onNotice(state.getHostName() + " abriu uma sala de Quiz ao vivo. Abra o plugin para entrar!");
        }
        if (state.getRoomId().equals(roomId)) {
            if (state.getPhase() == Phase.CLOSED) {
                dropRoom("O apresentador fechou a sala");
                return;
            }
            if (!joined && state.find(channel.selfId()) != null) {
                joined = true; // o ACK do JOIN se perdeu, mas o placar já me mostra
            }
            applyState(state);
        }
        publishRooms();
    }

    private void applyState(RoomState state) {
        long now = System.currentTimeMillis();
        if (state.getPhase() == Phase.QUESTION && state.getQuestionIndex() != seenQuestionIndex) {
            // Pergunta nova: o relógio do jogador começa agora, e o prazo vem relativo do apresentador.
            seenQuestionIndex = state.getQuestionIndex();
            questionSeenAt = now;
            localDeadline = now + state.getRemainingMillis();
            myChoice = -1;
            answerConfirmed = false;
        } else if (state.getPhase() == Phase.QUESTION) {
            // Corrige a deriva: o prazo só pode encurtar, nunca esticar.
            localDeadline = Math.min(localDeadline, now + state.getRemainingMillis());
        } else if (state.getPhase() == Phase.LOBBY) {
            seenQuestionIndex = -1;
            myChoice = -1;
        }
        current = state;
        publish();
    }

    private void dropRoom(String reason) {
        channel.cancelPendingTo(hostId);
        roomId = null;
        hostId = null;
        current = null;
        ui.onPlayer(null);
        ui.onNotice(reason);
        publishRooms();
    }

    private void expireRooms() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<SeenRoom> it = rooms.values().iterator();
        while (it.hasNext()) {
            SeenRoom r = it.next();
            if (now - r.lastSeen > ROOM_EXPIRY_MS) {
                it.remove();
                changed = true;
                if (r.state.getRoomId().equals(roomId)) {
                    dropRoom("Sem notícias do apresentador há 5 segundos. A sala caiu?");
                }
            }
        }
        if (changed) {
            publishRooms();
        }
    }

    private void publish() {
        if (roomId == null) {
            return;
        }
        ui.onPlayer(new PlayerSnapshot(current, channel.selfId(), hostName, joined, myChoice, answerConfirmed, localDeadline));
    }

    private void publishRooms() {
        List<RoomState> list = new ArrayList<>();
        for (SeenRoom r : rooms.values()) {
            list.add(r.state);
        }
        list.sort(Comparator.comparing(RoomState::getHostName, String.CASE_INSENSITIVE_ORDER));
        ui.onRooms(list);
    }

    private record SeenRoom(RoomState state, long lastSeen) {
    }
}
