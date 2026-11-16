package pitiupi.quiz.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lê perguntas de texto. Uma pergunta por linha:
 *
 * <pre>
 * Enunciado | alternativa 1 | alternativa 2 | alternativa 3 | alternativa 4 | número da correta (1-4)
 * </pre>
 *
 * Linhas vazias e linhas que começam com {@code #} são ignoradas.
 */
public final class QuestionBank {

    private static final String BUILT_IN = "/pitiupi/quiz/resources/perguntas-redes.txt";

    private QuestionBank() {
    }

    public static List<Question> builtIn() {
        try (InputStream in = QuestionBank.class.getResourceAsStream(BUILT_IN)) {
            if (in == null) {
                throw new IllegalStateException("Banco de perguntas embutido não encontrado no JAR");
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao ler o banco embutido", ex);
        }
    }

    public static List<Question> fromFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    static List<Question> parse(Reader source) throws IOException {
        List<Question> questions = new ArrayList<>();
        BufferedReader reader = new BufferedReader(source);
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = Arrays.stream(trimmed.split("\\|")).map(String::strip).toArray(String[]::new);
            if (parts.length != 6) {
                throw new IOException("Linha " + lineNumber + ": esperado 6 campos separados por |, veio " + parts.length);
            }
            int correct;
            try {
                correct = Integer.parseInt(parts[5]) - 1;
            } catch (NumberFormatException ex) {
                throw new IOException("Linha " + lineNumber + ": o último campo deve ser 1, 2, 3 ou 4");
            }
            if (correct < 0 || correct > 3) {
                throw new IOException("Linha " + lineNumber + ": o último campo deve ser 1, 2, 3 ou 4");
            }
            questions.add(new Question(parts[0], List.of(parts[1], parts[2], parts[3], parts[4]), correct));
        }
        if (questions.isEmpty()) {
            throw new IOException("Nenhuma pergunta encontrada");
        }
        return questions;
    }
}
