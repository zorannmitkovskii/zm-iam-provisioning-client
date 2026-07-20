package zm.iam.provisioning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import zm.iam.provisioning.client.dto.ServiceProvisioningManifest;
import zm.iam.provisioning.client.response.ApplyResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fires the manifest at IAM with exponential-backoff retry. Retries only
 * on transient failures (network, 5xx). A 4xx is a permanent client-side
 * problem — most often a validation error from IAM — so we surface it
 * immediately with the response body in the exception.
 *
 * <p>Backoff sequence starts at 1s and doubles each attempt; the total
 * elapsed time is capped by {@code iam.provisioning.retry-max-window-seconds}.
 * The very first attempt happens at t=0 (no initial delay).
 */
@Slf4j
public class IamProvisioningClient {

    private final IamProvisioningProperties props;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final RetrySleeper sleeper;

    public IamProvisioningClient(IamProvisioningProperties props, ObjectMapper mapper) {
        this(props, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                RetrySleeper.defaultSleeper());
    }

    /** Test constructor — inject a fake sleeper so retry timing doesn't
     *  blow past the JUnit timeout. */
    IamProvisioningClient(IamProvisioningProperties props,
                          ObjectMapper mapper,
                          HttpClient httpClient,
                          RetrySleeper sleeper) {
        this.props = props;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.sleeper = sleeper;
    }

    public ApplyResponse apply(ServiceProvisioningManifest manifest) {
        String url = trimTrailingSlash(props.getBaseUrl()) + "/provisioning/manifests";
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(manifest);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialise manifest to JSON", e);
        }

        long deadlineMillis = System.currentTimeMillis() + props.getRetryMaxWindowSeconds() * 1000L;
        long backoffMs = 1_000L;
        int attempt = 0;
        RuntimeException lastError = null;

        while (true) {
            attempt++;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("X-Provisioning-Token", props.getToken())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    return parseEnvelope(resp.body());
                }
                if (status >= 400 && status < 500) {
                    // Permanent — validation error, missing auth, ownership
                    // conflict. Log the body verbatim so the operator sees
                    // the field-level errors IAM returns.
                    throw new IamProvisioningException("IAM refused manifest with "
                            + status + " (non-retryable): " + resp.body(), status);
                }
                // 5xx — transient
                lastError = new IamProvisioningException(
                        "IAM returned " + status + " (transient): " + snippet(resp.body()), status);
            } catch (IamProvisioningException fatal) {
                if (!fatal.retryable()) throw fatal;
                lastError = fatal;
            } catch (IOException ioe) {
                lastError = new IamProvisioningException(
                        "Network error contacting IAM at " + url + ": " + ioe.getMessage(), 0, ioe);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IamProvisioningException("Interrupted while calling IAM", 0, ie);
            }

            long remainingMs = deadlineMillis - System.currentTimeMillis();
            if (remainingMs <= 0) {
                throw new IamProvisioningException(
                        "IAM apply failed after " + attempt + " attempt(s) within "
                                + props.getRetryMaxWindowSeconds() + "s window; last error: "
                                + lastError.getMessage(), lastError);
            }

            long sleepMs = Math.min(backoffMs, remainingMs);
            log.warn("[IamProvisioningClient] Attempt {} failed: {}; retrying in {}ms",
                    attempt, lastError.getMessage(), sleepMs);
            sleeper.sleep(sleepMs);
            backoffMs = Math.min(backoffMs * 2, 32_000L);
        }
    }

    private ApplyResponse parseEnvelope(String body) {
        try {
            ApplyResponse.Envelope env = mapper.readValue(body, ApplyResponse.Envelope.class);
            return env.data();
        } catch (IOException e) {
            throw new IamProvisioningException("Could not parse IAM response: " + body, 200, e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String snippet(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    /** Encapsulates any client-side failure. {@link #retryable} tells the
     *  loop whether to keep going. */
    public static class IamProvisioningException extends RuntimeException {
        private final int status;
        public IamProvisioningException(String msg, int status) { super(msg); this.status = status; }
        public IamProvisioningException(String msg, int status, Throwable cause) { super(msg, cause); this.status = status; }
        public IamProvisioningException(String msg, Throwable cause) { super(msg, cause); this.status = 0; }
        public boolean retryable() {
            // 4xx → don't retry; 5xx / network (status==0) → retry
            return status == 0 || status >= 500;
        }
    }

    /** Test seam. Default sleeps in real time; tests wire a zero-sleep. */
    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long millis);
        static RetrySleeper defaultSleeper() {
            return millis -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
        }
    }
}
