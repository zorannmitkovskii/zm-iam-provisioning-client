package zm.iam.provisioning.client.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RealmDeclaration(
        @NotBlank String name,
        @Valid RealmSettings settings,
        @Valid List<ClientDeclaration> clients,
        List<@NotBlank String> realmRoles,
        @Valid List<IdentityProviderDeclaration> identityProviders,
        List<@NotBlank String> userProfileAttributes
) {}
