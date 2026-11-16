package pitiupi.quiz.GUI;

import pitiupi.quiz.net.NetStats;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.DoubleConsumer;

/**
 * Rodapé "Rede": mostra o que o protocolo está fazendo por baixo do jogo.
 */
final class NetStatsBar extends JPanel {

    private static final double LOSS_RATE = 0.3;

    private final JLabel line = new JLabel();
    private final JCheckBox loss = new JCheckBox("Simular perda de 30% dos pacotes");

    NetStatsBar(DoubleConsumer onLossRate) {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        line.setForeground(Theme.TEXT_SOFT);
        line.setFont(Theme.font(13, false));
        loss.setOpaque(false);
        loss.setForeground(Theme.TEXT_SOFT);
        loss.setFont(Theme.font(13, true));
        loss.setFocusPainted(false);
        loss.setToolTipText("Descarta de propósito 30% dos datagramas que esta janela envia, para ver o ACK e a retransmissão recuperarem");
        loss.addActionListener(e -> onLossRate.accept(loss.isSelected() ? LOSS_RATE : 0));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(loss);
        add(line, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    void refresh(NetStats.Snapshot s, double answerDelayMs) {
        StringBuilder b = new StringBuilder("<html><b>Rede</b> · UDP multicast 224.0.0.3 &nbsp;|&nbsp; ");
        b.append("enviados <b>").append(s.sent()).append("</b> &nbsp; ");
        b.append("recebidos <b>").append(s.received()).append("</b> &nbsp; ");
        b.append("retransmissões <b>").append(s.retransmissions()).append("</b> &nbsp; ");
        b.append("duplicatas descartadas <b>").append(s.duplicates()).append("</b> &nbsp; ");
        b.append("ACKs <b>").append(s.acks()).append("</b> &nbsp; ");
        if (s.avgRttMillis() >= 0) {
            b.append("RTT (Karn) <b>").append(String.format("%.1f ms", s.avgRttMillis())).append("</b> &nbsp; ");
        }
        if (answerDelayMs >= 0) {
            b.append("atraso médio das respostas <b>").append(String.format("%.0f ms", answerDelayMs)).append("</b> &nbsp; ");
        }
        if (s.simulatedLost() > 0) {
            b.append("<font color='#FF9AA8'>perdidos (simulado) <b>").append(s.simulatedLost()).append("</b></font>");
        }
        b.append("</html>");
        line.setText(b.toString());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Widgets.smooth(g);
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}
