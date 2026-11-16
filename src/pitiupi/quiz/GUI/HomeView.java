package pitiupi.quiz.GUI;

import pitiupi.quiz.model.Phase;
import pitiupi.quiz.model.RoomState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tela inicial: criar uma sala ou entrar numa sala aberta na rede.
 */
final class HomeView extends JPanel {

    private final Supplier<String> userName;
    private final Consumer<String> onJoin;
    private final JLabel subtitle;
    private final JPanel roomList = new JPanel();
    private List<RoomState> lastRooms = List.of();

    HomeView(Supplier<String> userName, Runnable onCreate, Consumer<String> onJoin) {
        super(new BorderLayout(0, 18));
        this.userName = userName;
        this.onJoin = onJoin;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 18, 32));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Theme.centered("Quiz ao vivo", 46, true));
        subtitle = Theme.centered(" ", 18, false);
        subtitle.setForeground(Theme.TEXT_SOFT);
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 22, 0));
        columns.setOpaque(false);

        Widgets.Card create = new Widgets.Card(new BorderLayout(0, 14));
        create.add(Theme.label("Apresentar", 26, true), BorderLayout.NORTH);
        WrapText how = new WrapText(17, false, false);
        how.setText("Crie uma sala e projete sua tela. A turma entra pelo próprio Pitiupi, "
                + "você controla as perguntas e o placar aparece ao vivo.\n\n"
                + "Perguntas sobre Redes de Computadores já vêm embutidas; dá para carregar as suas num .txt.");
        create.add(how, BorderLayout.CENTER);
        Widgets.PillButton createButton = new Widgets.PillButton("Criar sala  ▶", Theme.ACCENT, 22);
        createButton.addActionListener(e -> onCreate.run());
        create.add(createButton, BorderLayout.SOUTH);
        columns.add(create);

        Widgets.Card join = new Widgets.Card(new BorderLayout(0, 14));
        join.add(Theme.label("Salas abertas na rede", 26, true), BorderLayout.NORTH);
        roomList.setOpaque(false);
        roomList.setLayout(new BoxLayout(roomList, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(roomList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(BorderFactory.createEmptyBorder());
        join.add(scroll, BorderLayout.CENTER);
        columns.add(join);

        add(columns, BorderLayout.CENTER);
        refreshName();
        setRooms(List.of());
    }

    void refreshName() {
        subtitle.setText("Você está na rede como " + userName.get());
    }

    void setRooms(List<RoomState> rooms) {
        if (sameRooms(rooms)) {
            return;
        }
        lastRooms = rooms;
        roomList.removeAll();
        if (rooms.isEmpty()) {
            WrapText empty = new WrapText(17, false, false);
            empty.setPreferredSize(new Dimension(300, 110));
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setText("Procurando salas…\n\nQuando alguém criar uma sala na mesma rede e na mesma porta, ela aparece aqui.");
            empty.setForeground(Theme.TEXT_SOFT);
            roomList.add(empty);
        }
        for (RoomState room : rooms) {
            roomList.add(row(room));
            roomList.add(Box.createVerticalStrut(10));
        }
        roomList.revalidate();
        roomList.repaint();
    }

    private boolean sameRooms(List<RoomState> rooms) {
        if (rooms.size() != lastRooms.size() || roomList.getComponentCount() == 0) {
            return false;
        }
        for (int i = 0; i < rooms.size(); i++) {
            RoomState a = rooms.get(i);
            RoomState b = lastRooms.get(i);
            if (!a.getRoomId().equals(b.getRoomId()) || a.getPhase() != b.getPhase()
                    || a.getPlayers().size() != b.getPlayers().size() || a.getQuestionIndex() != b.getQuestionIndex()) {
                return false;
            }
        }
        return true;
    }

    private Component row(RoomState room) {
        Widgets.Card card = new Widgets.Card(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Theme.label("Sala de " + room.getHostName(), 20, true));
        int n = room.getPlayers().size();
        String status = room.getPhase() == Phase.LOBBY
                ? "aguardando início"
                : room.getPhase() == Phase.END ? "partida encerrada"
                : "em andamento · pergunta " + (room.getQuestionIndex() + 1) + " de " + room.getQuestionCount();
        JLabel sub = Theme.label(n + (n == 1 ? " jogador" : " jogadores") + " · " + status, 15, false);
        sub.setForeground(Theme.TEXT_SOFT);
        text.add(sub);
        card.add(text, BorderLayout.CENTER);
        Widgets.PillButton enter = new Widgets.PillButton("Entrar", Theme.OPTION_COLORS[1], 18);
        enter.addActionListener(e -> onJoin.accept(room.getRoomId()));
        card.add(enter, BorderLayout.EAST);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }
}
