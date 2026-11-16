package pitiupi.quiz.model;

import java.io.Serializable;
import java.util.List;

/**
 * Uma pergunta de múltipla escolha com quatro alternativas.
 */
public final class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String text;
    private final String[] options;
    private final int correct;

    public Question(String text, List<String> options, int correct) {
        if (options.size() != 4) {
            throw new IllegalArgumentException("A pergunta precisa de exatamente 4 alternativas");
        }
        if (correct < 0 || correct > 3) {
            throw new IllegalArgumentException("Alternativa correta fora de 1..4");
        }
        this.text = text;
        this.options = options.toArray(new String[0]);
        this.correct = correct;
    }

    public String getText() {
        return text;
    }

    public String[] getOptions() {
        return options.clone();
    }

    /** Índice da alternativa correta, de 0 a 3. */
    public int getCorrect() {
        return correct;
    }
}
