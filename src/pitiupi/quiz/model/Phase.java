package pitiupi.quiz.model;

/**
 * Fases da sala. O apresentador é a única fonte da verdade: os jogadores só espelham
 * a fase que chega no {@link RoomState}.
 */
public enum Phase {
    LOBBY,
    QUESTION,
    REVEAL,
    END,
    CLOSED
}
