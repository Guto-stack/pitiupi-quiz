package pitiupi.quiz.model;

import java.io.Serializable;

/**
 * Linha do placar. Imutável: o apresentador gera uma lista nova a cada mudança.
 */
public final class PlayerScore implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Resultado do jogador na última pergunta revelada. */
    public static final int NO_ANSWER = -1;
    public static final int WRONG = 0;
    public static final int RIGHT = 1;

    private final String id;
    private final String name;
    private final int score;
    private final int lastDelta;
    private final int lastResult;
    private final int streak;

    public PlayerScore(String id, String name, int score, int lastDelta, int lastResult, int streak) {
        this.id = id;
        this.name = name;
        this.score = score;
        this.lastDelta = lastDelta;
        this.lastResult = lastResult;
        this.streak = streak;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public int getLastDelta() {
        return lastDelta;
    }

    public int getLastResult() {
        return lastResult;
    }

    public int getStreak() {
        return streak;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlayerScore other)) {
            return false;
        }
        return score == other.score
                && lastDelta == other.lastDelta
                && lastResult == other.lastResult
                && streak == other.streak
                && id.equals(other.id)
                && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name, score, lastDelta, lastResult, streak);
    }
}
