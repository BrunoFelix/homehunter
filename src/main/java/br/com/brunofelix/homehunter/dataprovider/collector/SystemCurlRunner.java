package br.com.brunofelix.homehunter.dataprovider.collector;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Slf4j
final class SystemCurlRunner implements GlueApiCollectorSupport.CurlRunner {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final String portalLabel;
    private final String xDomain;
    private final String tempFilePrefix;
    private final String curlCommand;
    private final int timeoutSeconds;

    SystemCurlRunner(String portalLabel, String xDomain, String tempFilePrefix) {
        this(portalLabel, xDomain, tempFilePrefix, curlCommandForCurrentOs(), 30);
    }

    SystemCurlRunner(String portalLabel, String xDomain, String tempFilePrefix, String curlCommand, int timeoutSeconds) {
        this.portalLabel = portalLabel;
        this.xDomain = xDomain;
        this.tempFilePrefix = tempFilePrefix;
        this.curlCommand = curlCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    private static String curlCommandForCurrentOs() {
        String configured = System.getenv("HOMEHUNTER_CURL_BIN");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "curl.exe" : "curl";
    }

    @Override
    public GlueApiCollectorSupport.CurlResult execute(String url) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(tempFilePrefix, ".json");
            ProcessBuilder pb = new ProcessBuilder(
                    curlCommand,
                    "--noproxy", "*",
                    "-s",
                    "--max-time", String.valueOf(timeoutSeconds),
                    "-H", "x-domain: " + xDomain,
                    "-H", "User-Agent: " + USER_AGENT,
                    "-H", "Accept: application/json",
                    "-o", tmp.toString(),
                    "-w", "%{http_code}",
                    url);
            Process process = pb.redirectErrorStream(true).start();
            String stdout;
            try (InputStream is = process.getInputStream()) {
                stdout = new String(is.readAllBytes(), StandardCharsets.US_ASCII).trim();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("{} curl exited {}: {}", portalLabel, exitCode, stdout);
                return GlueApiCollectorSupport.CurlResult.failure();
            }
            int status = parseStatus(stdout);
            byte[] body = Files.readAllBytes(tmp);
            return new GlueApiCollectorSupport.CurlResult(status, body);
        } catch (IOException e) {
            log.warn("{} curl fetch failed: {}", portalLabel, e.getMessage());
            return GlueApiCollectorSupport.CurlResult.failure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GlueApiCollectorSupport.CurlResult.failure();
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    private int parseStatus(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(stdout.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}