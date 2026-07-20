package zm.iam.provisioning.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Slim view of IAM's {@code ApplyResult}. Enough to log the outcome; the
 * client doesn't need every field IAM tracks internally.
 *
 * <p>Ignores unknown properties so IAM can add fields to its response
 * without forcing every consumer to upgrade.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplyResponse(
        String status,          // APPLIED | NOOP
        String serviceId,
        Integer appliedVersion,
        List<ChangeEntry> changes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangeEntry(String resource, String name, String action, String details) {}

    /** IAM wraps ApplyResult inside ApiResponse.data — this record
     *  mirrors that envelope. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(boolean success, String message, ApplyResponse data) {}
}
