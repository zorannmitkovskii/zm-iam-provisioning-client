package zm.iam.provisioning.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "iam.provisioning")
public class IamProvisioningProperties {

    /** Master kill switch. When false, the auto-configuration skips the
     *  whole flow — useful for local development without a live IAM. */
    private boolean enabled = true;

    /** Where IAM lives. e.g. {@code http://iam:8383} inside a Docker
     *  network, {@code https://iam.internal} in prod. */
    private String baseUrl;

    /** The plaintext bootstrap token — the SERVICE-side counterpart to
     *  IAM's {@code IAM_PROVISIONING_TOKEN_<SERVICEID>} argon2 hash.
     *  Never log. */
    private String token;

    /** Location of the manifest. Default is
     *  {@code classpath:iam-manifest.yml} which lands in
     *  {@code src/main/resources/iam-manifest.yml}. */
    private String manifestPath = "classpath:iam-manifest.yml";

    /** Total time the client will keep retrying transient failures
     *  (network, 5xx) before giving up. 60s covers a slow Keycloak +
     *  Postgres cold start on shared infrastructure. */
    private int retryMaxWindowSeconds = 60;

    /** What to do when the retry window is exhausted:
     *  <ul>
     *    <li>{@link FailureMode#FAIL_FAST} — throw, kill the app. Prod
     *        default: a service without its IAM setup is worse than a
     *        dead process that compose can restart.</li>
     *    <li>{@link FailureMode#LOG_AND_CONTINUE} — ERROR log, keep
     *        going. Local dev without a live IAM.</li>
     *  </ul> */
    private FailureMode failureMode = FailureMode.FAIL_FAST;

    public enum FailureMode {
        FAIL_FAST,
        LOG_AND_CONTINUE
    }
}
