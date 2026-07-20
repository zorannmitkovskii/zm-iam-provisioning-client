package zm.iam.provisioning.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import zm.iam.provisioning.client.dto.ServiceProvisioningManifest;
import zm.iam.provisioning.client.response.ApplyResponse;

/**
 * Fires once on {@link ApplicationReadyEvent}. Loads the manifest,
 * ships it to IAM, and applies the caller's {@code failure-mode}
 * decision:
 *
 * <ul>
 *   <li>{@link IamProvisioningProperties.FailureMode#FAIL_FAST} — re-throw
 *       so Spring aborts startup. Compose / systemd sees a failed
 *       process and restarts it, giving IAM (which may be starting
 *       right now) another window to come up.</li>
 *   <li>{@link IamProvisioningProperties.FailureMode#LOG_AND_CONTINUE} —
 *       ERROR log, no throw. Used in local dev when IAM is intentionally
 *       absent.</li>
 * </ul>
 */
@Slf4j
public class ProvisioningStartupListener {

    private final IamProvisioningProperties props;
    private final ManifestLoader loader;
    private final IamProvisioningClient client;

    public ProvisioningStartupListener(IamProvisioningProperties props,
                                       ManifestLoader loader,
                                       IamProvisioningClient client) {
        this.props = props;
        this.loader = loader;
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            ServiceProvisioningManifest manifest = loader.load(props.getManifestPath());
            ApplyResponse response = client.apply(manifest);
            logSuccess(manifest, response);
        } catch (RuntimeException e) {
            handleFailure(e);
        }
    }

    private void logSuccess(ServiceProvisioningManifest manifest, ApplyResponse response) {
        if ("NOOP".equals(response.status())) {
            log.info("[IamProvisioningClient] NOOP — serviceId='{}' version {} already applied",
                    manifest.serviceId(), manifest.manifestVersion());
        } else {
            int changeCount = response.changes() == null ? 0 : response.changes().size();
            log.info("[IamProvisioningClient] APPLIED — serviceId='{}' version={} changes={}",
                    manifest.serviceId(), response.appliedVersion(), changeCount);
            if (response.changes() != null) {
                for (ApplyResponse.ChangeEntry c : response.changes()) {
                    log.info("[IamProvisioningClient]   {} {} {}{}",
                            c.action(), c.resource(), c.name(),
                            c.details() == null ? "" : " (" + c.details() + ")");
                }
            }
        }
    }

    private void handleFailure(RuntimeException e) {
        if (props.getFailureMode() == IamProvisioningProperties.FailureMode.LOG_AND_CONTINUE) {
            log.error("[IamProvisioningClient] Provisioning FAILED (log-and-continue): {}",
                    e.getMessage());
            return;
        }
        log.error("[IamProvisioningClient] Provisioning FAILED (fail-fast) — aborting startup: {}",
                e.getMessage());
        throw e;
    }
}
