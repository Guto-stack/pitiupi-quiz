package pitiupi.quiz.net;

import pitiupi.models.Messages.Message;
import pitiupi.quiz.model.RoomState;

/**
 * Única mensagem do protocolo do Quiz. Viaja sempre por UDP multicast (grupo 224.0.0.3).
 *
 * <ul>
 *   <li>{@code from}: id aleatório da instância que enviou (cada janela do Pitiupi gera o seu).</li>
 *   <li>{@code to}: id do destinatário, ou {@code null} quando é para todos. Quem não é o destinatário
 *       descarta a mensagem.</li>
 *   <li>{@code msgId}: número da mensagem lógica. Retransmissões e cópias repetem o mesmo número,
 *       e é assim que o receptor reconhece uma duplicata.</li>
 *   <li>{@code needsAck}: o receptor responde com {@link Type#ACK} e o emissor retransmite até receber.</li>
 * </ul>
 */
public final class QuizMessage extends Message {

    private static final long serialVersionUID = 1L;

    public enum Type {
        /** Apresentador → todos: retrato completo da sala ({@link RoomState}). */
        STATE,
        /** Jogador → apresentador: quero entrar na sala. Confiável. */
        JOIN,
        /** Jogador → apresentador: minha resposta. Confiável. */
        ANSWER,
        /** Jogador → apresentador: saí da sala. */
        LEAVE,
        /** Confirmação de recebimento de uma mensagem com {@code needsAck}. */
        ACK
    }

    private Type type;
    private long msgId;
    private String from;
    private String to;
    private String fromName;
    private String roomId;
    private boolean needsAck;

    private RoomState state;
    private int questionIndex;
    private int choice;
    private long elapsedMillis;
    private long ackFor;

    public QuizMessage() {
    }

    private QuizMessage(Type type, String from, String fromName, String to, String roomId) {
        this.type = type;
        this.from = from;
        this.fromName = fromName;
        this.to = to;
        this.roomId = roomId;
    }

    public static QuizMessage state(String hostId, String hostName, RoomState state) {
        QuizMessage m = new QuizMessage(Type.STATE, hostId, hostName, null, state.getRoomId());
        m.state = state;
        return m;
    }

    public static QuizMessage join(String playerId, String playerName, String hostId, String roomId) {
        return new QuizMessage(Type.JOIN, playerId, playerName, hostId, roomId);
    }

    public static QuizMessage leave(String playerId, String playerName, String hostId, String roomId) {
        return new QuizMessage(Type.LEAVE, playerId, playerName, hostId, roomId);
    }

    public static QuizMessage answer(
            String playerId,
            String playerName,
            String hostId,
            String roomId,
            int questionIndex,
            int choice,
            long elapsedMillis
    ) {
        QuizMessage m = new QuizMessage(Type.ANSWER, playerId, playerName, hostId, roomId);
        m.questionIndex = questionIndex;
        m.choice = choice;
        m.elapsedMillis = elapsedMillis;
        return m;
    }

    static QuizMessage ack(QuizMessage received, String selfId) {
        QuizMessage m = new QuizMessage(Type.ACK, selfId, null, received.from, received.roomId);
        m.ackFor = received.msgId;
        return m;
    }

    public Type getType() {
        return type;
    }

    public long getMsgId() {
        return msgId;
    }

    void setMsgId(long msgId) {
        this.msgId = msgId;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getFromName() {
        return fromName;
    }

    public String getRoomId() {
        return roomId;
    }

    public boolean isNeedsAck() {
        return needsAck;
    }

    void setNeedsAck(boolean needsAck) {
        this.needsAck = needsAck;
    }

    public RoomState getState() {
        return state;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public int getChoice() {
        return choice;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    long getAckFor() {
        return ackFor;
    }
}
