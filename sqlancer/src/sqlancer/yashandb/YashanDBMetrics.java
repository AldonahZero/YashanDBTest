package sqlancer.yashandb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

public final class YashanDBMetrics {
    private static final AtomicLong ATTEMPTED = new AtomicLong();
    private static final AtomicLong COMPLETED = new AtomicLong();
    private static final AtomicLong SKIPPED = new AtomicLong();
    private static final AtomicLong MISMATCHED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(YashanDBMetrics::write));
    }

    private YashanDBMetrics() {
    }

    public static void attempted() {
        ATTEMPTED.incrementAndGet();
    }

    public static void completed() {
        if (COMPLETED.incrementAndGet() % 100 == 0) {
            write();
        }
    }

    public static void skipped() {
        SKIPPED.incrementAndGet();
        write();
    }

    public static void mismatched() {
        MISMATCHED.incrementAndGet();
        write();
    }

    public static void failed() {
        FAILED.incrementAndGet();
        write();
    }

    private static synchronized void write() {
        String json = String.format("{\"attempted\":%d,\"completed\":%d,\"skipped\":%d,"
                + "\"mismatched\":%d,\"failed\":%d}%n", ATTEMPTED.get(), COMPLETED.get(), SKIPPED.get(),
                MISMATCHED.get(), FAILED.get());
        try {
            Path temporary = Path.of("oracle-metrics.json.tmp");
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, Path.of("oracle-metrics.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Cannot save YashanDB metrics: " + e.getMessage());
        }
    }
}
