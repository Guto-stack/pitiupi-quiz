package pitiupi.quiz.net;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Contadores do protocolo, exibidos no painel "Rede".
 */
public final class NetStats {

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong retransmissions = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong simulatedLost = new AtomicLong();
    private final AtomicLong acksReceived = new AtomicLong();
    private final AtomicLong rttSum = new AtomicLong();
    private final AtomicLong rttCount = new AtomicLong();
    private final AtomicLong lastRtt = new AtomicLong(-1);

    void sent() {
        sent.incrementAndGet();
    }

    void received() {
        received.incrementAndGet();
    }

    void retransmission() {
        retransmissions.incrementAndGet();
    }

    void duplicate() {
        duplicates.incrementAndGet();
    }

    void simulatedLoss() {
        simulatedLost.incrementAndGet();
    }

    void ack() {
        acksReceived.incrementAndGet();
    }

    void rtt(long millis) {
        rttSum.addAndGet(millis);
        rttCount.incrementAndGet();
        lastRtt.set(millis);
    }

    public Snapshot snapshot() {
        long count = rttCount.get();
        return new Snapshot(
                sent.get(),
                received.get(),
                retransmissions.get(),
                duplicates.get(),
                simulatedLost.get(),
                acksReceived.get(),
                count == 0 ? -1 : (double) rttSum.get() / count,
                lastRtt.get()
        );
    }

    public record Snapshot(
            long sent,
            long received,
            long retransmissions,
            long duplicates,
            long simulatedLost,
            long acks,
            double avgRttMillis,
            long lastRttMillis
    ) {
    }
}
