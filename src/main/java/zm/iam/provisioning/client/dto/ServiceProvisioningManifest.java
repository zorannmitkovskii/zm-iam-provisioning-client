package zm.iam.provisioning.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The declaration a service ships to IAM. Client-side copy of the
 * server DTO — kept in sync via contract tests. Validation runs
 * client-side too so a typo in {@code iam-manifest.yml} fails at
 * startup with a clear message rather than getting a 400 from IAM.
 */
public record ServiceProvisioningManifest(
        @NotBlank
        @Pattern(regexp = "[a-z0-9-]+", message = "serviceId must match [a-z0-9-]+")
        @Size(max = 64)
        String serviceId,

        @Positive int manifestVersion,

        @NotEmpty @Valid List<RealmDeclaration> realms
) {
    public static final String ZM_SERVICES_REALM = "zm-services";

    @JsonIgnore
    @AssertTrue(message = "realm names must be unique across the manifest")
    public boolean isRealmNamesUnique() {
        if (realms == null) return true;
        return realms.stream().map(RealmDeclaration::name).filter(Objects::nonNull).distinct().count()
                == realms.stream().map(RealmDeclaration::name).filter(Objects::nonNull).count();
    }

    @JsonIgnore
    @AssertTrue(message = "in zm-services realm only clients with clientId '{serviceId}-svc' are allowed")
    public boolean isZmServicesScopeRespected() {
        if (realms == null || serviceId == null) return true;
        String expected = serviceId + "-svc";
        return realms.stream()
                .filter(r -> ZM_SERVICES_REALM.equals(r.name()))
                .flatMap(r -> r.clients() == null ? Stream.<ClientDeclaration>empty() : r.clients().stream())
                .allMatch(c -> expected.equals(c.clientId()));
    }
}
