package pitiupi.quiz.control;

import pitiupi.quiz.model.RoomState;

import java.util.List;

/**
 * O que a interface recebe dos controllers. As chamadas chegam na thread do protocolo;
 * a implementação Swing repassa para a EDT.
 */
public interface QuizUi {

    /** Salas abertas na rede (sem a minha, se eu for o apresentador). */
    void onRooms(List<RoomState> rooms);

    /** Novo estado da sala que eu apresento; {@code null} quando a sala foi fechada. */
    void onHost(HostSnapshot snapshot);

    /** Novo estado da sala em que eu jogo; {@code null} quando saí. */
    void onPlayer(PlayerSnapshot snapshot);

    /** Aviso curto para a barra de status. */
    void onNotice(String text);
}
