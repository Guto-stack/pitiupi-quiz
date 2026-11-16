package pitiupi.quiz.control;

import pitiupi.quiz.model.RoomState;

/**
 * O que a tela do apresentador precisa além do {@link RoomState} público.
 *
 * @param state            retrato da sala
 * @param localDeadline    instante local em que a pergunta atual encerra
 * @param questionSource   de onde vieram as perguntas (embutidas ou arquivo)
 * @param availableQuestions quantas perguntas a fonte tem
 * @param avgAnswerDelayMs atraso médio medido nas respostas (rede ida e volta), -1 sem amostras
 */
public record HostSnapshot(
        RoomState state,
        long localDeadline,
        String questionSource,
        int availableQuestions,
        double avgAnswerDelayMs
) {
}
