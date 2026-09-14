package br.com.brunofelix.homehunter.dataprovider.collector;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Random;

@Slf4j
public final class Throttle {

    private static final double JITTER_RATIO = 0.3;

    private final long politenessDelayMs;
    private final int pauseEveryPages;
    private final long pauseDurationMs;
    private final Random random = new Random();

    private Throttle(Duration politenessDelay, int pauseEveryPages, Duration pauseDuration) {
        this.politenessDelayMs = politenessDelay == null ? 0 : politenessDelay.toMillis();
        this.pauseEveryPages = pauseEveryPages;
        this.pauseDurationMs = pauseDuration == null ? 0 : pauseDuration.toMillis();
    }

    public static Throttle of(Duration politenessDelay, int pauseEveryPages, Duration pauseDuration) {
        return new Throttle(politenessDelay, pauseEveryPages, pauseDuration);
    }

    public static Throttle none() {
        return new Throttle(Duration.ZERO, 0, Duration.ZERO);
    }

    public void apply(int page, String portalLabel) {
        if (politenessDelayMs > 0) {
            long jitter = Math.max(1, (long) (politenessDelayMs * JITTER_RATIO));
            long wait = politenessDelayMs + random.nextLong(-jitter, jitter + 1);
            sleep(wait, portalLabel + " politeness delay");
        }
        if (pauseEveryPages > 0 && pauseDurationMs > 0 && page > 1 && (page - 1) % pauseEveryPages == 0) {
            sleep(pauseDurationMs, portalLabel + " sleeping after " + (page - 1) + " page(s) to avoid being rate-limited as DDoS");
        }
    }

    private void sleep(long millis, String message) {
        log.info("{} of {} ms...", message, millis);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Throttle interrupted while waiting; continuing.");
        }
    }
}