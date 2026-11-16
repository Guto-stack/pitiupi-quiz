package pitiupi.quiz.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;
import pitiupi.quiz.GUI.QuizPanel;
import pitiupi.quiz.control.QuizCore;
import pitiupi.quiz.net.QuizMessage;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Quiz ao vivo: um apresentador manda perguntas para a sala inteira por UDP multicast e os
 * jogadores respondem contra o relógio. A confiabilidade (ACK, retransmissão, duplicatas)
 * é feita pelo próprio plugin, em {@link pitiupi.quiz.net.ReliableChannel}.
 */
public class QuizPlugin extends Plugin {

    private PluginWorkspace workspace;
    private QuizCore core;
    private QuizPanel panel;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.panel = new QuizPanel(connectionManager::getUserName, this::openTab, this::status);
        this.core = new QuizCore(connectionManager, panel);
        panel.attach(core);
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());
        Runtime.getRuntime().addShutdownHook(new Thread(core::shutdown, "quiz-shutdown"));
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "quiz.open",
                "Abrir Quiz ao vivo",
                PeerActionTarget.ALL_PEERS,
                target -> openTab()
        ));
        registrar.register(PeerAction.of(
                "quiz.open.peer",
                "Jogar Quiz ao vivo",
                PeerActionTarget.SINGLE_PEER,
                target -> openTab()
        ));
    }

    private void openTab() {
        SwingUtilities.invokeLater(() -> workspace.openOrFocusTab(getIdentifier(), getDisplayName()));
    }

    private void status(String text) {
        SwingUtilities.invokeLater(() -> workspace.setStatusMessage(text));
    }

    @Override
    public void onShellTabClosed() {
        // Fechar a aba só esconde a tela: a sala (ou a partida) continua rodando.
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(QuizMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (message instanceof QuizMessage quizMessage && core != null) {
            core.onDatagram(quizMessage);
        }
    }

    @Override
    public String getName() {
        return "Quiz";
    }

    @Override
    public String getDisplayName() {
        return "Quiz ao vivo";
    }

    @Override
    public String getDescription() {
        return "Quiz em tempo real para a sala inteira, sobre UDP multicast com ACK e retransmissão";
    }

    @Override
    public String getAuthor() {
        return "Augusto";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getIdentifier() {
        return "pitiupi.quiz";
    }
}
