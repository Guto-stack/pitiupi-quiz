package pitiupi.quiz.control;

import pitiupi.quiz.model.RoomState;

/**
 * Visão do jogador sobre a sala.
 *
 * @param state          último retrato recebido do apresentador ({@code null} enquanto o JOIN viaja)
 * @param selfId         meu id na rede
 * @param hostName       nome do apresentador
 * @param joined         o apresentador já confirmou minha entrada
 * @param myChoice       alternativa que escolhi na pergunta atual, -1 se ainda não escolhi
 * @param answerConfirmed o apresentador confirmou (ACK) minha resposta
 * @param localDeadline  instante local em que a pergunta atual encerra
 */
public record PlayerSnapshot(
        RoomState state,
        String selfId,
        String hostName,
        boolean joined,
        int myChoice,
        boolean answerConfirmed,
        long localDeadline
) {
}
