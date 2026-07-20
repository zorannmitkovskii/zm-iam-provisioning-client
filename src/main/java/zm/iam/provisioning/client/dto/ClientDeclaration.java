package zm.iam.provisioning.client.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ClientDeclaration(
        @NotBlank
        @Pattern(regexp = "[a-zA-Z0-9-_]+", message = "clientId must match [a-zA-Z0-9-_]+")
        String clientId,
        @NotNull ClientType type,
        Boolean pkce,
        List<@NotBlank String> redirectUris,
        List<@NotBlank String> webOrigins,
        Boolean serviceAccountsEnabled,
        List<@NotBlank String> serviceAccountRoles,
        @Valid List<ProtocolMapperDeclaration> protocolMappers
) {}
