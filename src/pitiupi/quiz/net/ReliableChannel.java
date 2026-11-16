package pitiupi.quiz.net;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.control.ConnectionManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Canal do Quiz sobre UDP multicast, com confiabilidade feita na aplicação.
 *
 * <p>UDP não garante entrega nem descarta repetições. Este canal acrescenta:</p>
 * <ul>
 *   <li><b>ACK + retransmissão</b> ({@link #sendReliable}): a mensagem é reenviada a cada
 *       {@value #RETRANSMIT_MS} ms até chegar a confirmação, ou até o prazo esgotar.</li>
 *   <li><b>Descarte de duplicatas</b>: cada mensagem lógica tem um {@code msgId}. O par
 *       (remetente, msgId) já visto é ignorado, mas o ACK é reenviado, porque o ACK anterior pode ter se perdido.</li>
 *   <li><b>RTT pelo algoritmo de Karn</b>: só mede o tempo de ida e volta de mensagens confirmadas
 *       sem retransmissão; com retransmissão não dá para saber a qual envio o ACK responde.</li>
 *   <li><b>Simulação de perda</b>: descarta de propósito uma fração dos envios, para demonstrar a recuperação.</li>
 * </ul>
 *
 * <p>Toda a lógica do Quiz roda numa única thread ({@link #execute}), o que dispensa travas nos controllers.</p>
 */
public final class ReliableChannel {

    public static final long RETRANSMIT_MS = 500;
    private static final int SEEN_LIMIT = 10_000;

    private final ConnectionManager connectionManager;
    private final String selfId = UUID.randomUUID().toString();
    private final ScheduledExecutorService executor;
    private final NetStats stats = new NetStats();
    private final Map<Long, Pending> pending = new HashMap<>();
    private final Map<String, Boolean> seen = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SEEN_LIMIT;
        }
    };

    private Consumer<QuizMessage> handler = m -> { };
    private long nextMsgId = 1;
    private volatile double simulatedLossRate;

    public ReliableChannel(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "quiz-protocol");
            t.setDaemon(true);
            return t;
        });
    }

    public String selfId() {
        return selfId;
    }

    public NetStats stats() {
        return stats;
    }

    public void setHandler(Consumer<QuizMessage> handler) {
        this.handler = handler;
    }

    public void setSimulatedLossRate(double rate) {
        this.simulatedLossRate = Math.max(0, Math.min(0.9, rate));
    }

    public double getSimulatedLossRate() {
        return simulatedLossRate;
    }

    /** Roda {@code task} na thread do protocolo. */
    public void execute(Runnable task) {
        executor.execute(safe(task));
    }

    public ScheduledFuture<?> schedule(Runnable task, long delayMs) {
        return executor.schedule(safe(task), delayMs, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> every(Runnable task, long periodMs) {
        return executor.scheduleAtFixedRate(safe(task), 0, periodMs, TimeUnit.MILLISECONDS);
    }

    /** Chamado pelo plugin, na thread de rede do núcleo. */
    public void onDatagram(QuizMessage message) {
        execute(() -> handle(message));
    }

    /** Envio simples, sem confirmação. */
    public void send(QuizMessage message) {
        stamp(message);
        transmit(message);
    }

    /**
     * Envia {@code copies} cópias da mesma mensagem lógica, espaçadas. Útil em multicast,
     * onde pedir ACK de cada pessoa da sala multiplicaria o tráfego.
     */
    public void sendBurst(QuizMessage message, int copies, long intervalMs) {
        stamp(message);
        transmit(message);
        for (int i = 1; i < copies; i++) {
            schedule(() -> {
                stats.retransmission();
                transmit(message);
            }, intervalMs * i);
        }
    }

    /**
     * Envia e retransmite até o ACK do destinatário ({@code onAcked}). Se o prazo acabar, chama {@code onGiveUp}.
     */
    public void sendReliable(QuizMessage message, long giveUpAfterMs, Runnable onAcked, Runnable onGiveUp) {
        stamp(message);
        message.setNeedsAck(true);
        Pending p = new Pending(message, System.currentTimeMillis(), giveUpAfterMs, onAcked, onGiveUp);
        pending.put(message.getMsgId(), p);
        transmit(message);
        p.timer = executor.scheduleAtFixedRate(safe(() -> retransmit(p)), RETRANSMIT_MS, RETRANSMIT_MS, TimeUnit.MILLISECONDS);
    }

    /** Cancela retransmissões pendentes para um destinatário (ex.: saí da sala). */
    public void cancelPendingTo(String to) {
        Iterator<Pending> it = pending.values().iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (to == null || to.equals(p.message.getTo())) {
                p.timer.cancel(false);
                it.remove();
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void retransmit(Pending p) {
        if (!pending.containsKey(p.message.getMsgId())) {
            p.timer.cancel(false);
            return;
        }
        if (System.currentTimeMillis() - p.firstSentAt > p.giveUpAfterMs) {
            pending.remove(p.message.getMsgId());
            p.timer.cancel(false);
            if (p.onGiveUp != null) {
                p.onGiveUp.run();
            }
            return;
        }
        p.attempts++;
        stats.retransmission();
        transmit(p.message);
    }

    private void handle(QuizMessage m) {
        if (m.getFrom() == null || m.getType() == null || selfId.equals(m.getFrom())) {
            return; // o multicast volta para quem enviou (loopback)
        }
        if (m.getTo() != null && !selfId.equals(m.getTo())) {
            return; // dirigida a outra pessoa
        }
        stats.received();

        if (m.getType() == QuizMessage.Type.ACK) {
            Pending p = pending.remove(m.getAckFor());
            if (p != null) {
                p.timer.cancel(false);
                stats.ack();
                if (p.attempts == 1) {
                    stats.rtt(System.currentTimeMillis() - p.firstSentAt);
                }
                if (p.onAcked != null) {
                    p.onAcked.run();
                }
            }
            return;
        }

        String key = m.getFrom() + ":" + m.getMsgId();
        boolean duplicate = seen.containsKey(key);
        if (m.isNeedsAck()) {
            send(QuizMessage.ack(m, selfId));
        }
        if (duplicate) {
            stats.duplicate();
            return;
        }
        seen.put(key, Boolean.TRUE);
        handler.accept(m);
    }

    private void stamp(QuizMessage message) {
        if (message.getMsgId() == 0) {
            message.setMsgId(nextMsgId++);
        }
    }

    private void transmit(QuizMessage message) {
        if (simulatedLossRate > 0 && ThreadLocalRandom.current().nextDouble() < simulatedLossRate) {
            stats.simulatedLoss();
            return;
        }
        int sent = MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
        if (sent > 0) {
            stats.sent();
        }
    }

    private static Runnable safe(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                System.err.println("[Quiz] erro no protocolo: " + ex);
                ex.printStackTrace();
            }
        };
    }

    private static final class Pending {
        final QuizMessage message;
        final long firstSentAt;
        final long giveUpAfterMs;
        final Runnable onAcked;
        final Runnable onGiveUp;
        int attempts = 1;
        ScheduledFuture<?> timer;

        Pending(QuizMessage message, long firstSentAt, long giveUpAfterMs, Runnable onAcked, Runnable onGiveUp) {
            this.message = message;
            this.firstSentAt = firstSentAt;
            this.giveUpAfterMs = giveUpAfterMs;
            this.onAcked = onAcked;
            this.onGiveUp = onGiveUp;
        }
    }
}
