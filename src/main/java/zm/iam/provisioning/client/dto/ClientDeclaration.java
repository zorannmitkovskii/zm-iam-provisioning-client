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
        /** Opt-in to the OAuth2 password (ROPC) grant. Needed by a client whose
         *  frontend logs in through IAM's {@code /public/users/login}; the
         *  browser-redirect flow does not use it. Must stay in step with
         *  zm-iam-service's own ClientDeclaration — this record is parsed with
         *  unknown properties rejected, so a field IAM knows and this does not
         *  fails the manifest before it is ever sent. */
        Boolean directAccessGrantsEnabled,
        /** Name of the env var on zm-iam-service holding this client's secret —
         *  the name, never the secret. Only meaningful for CONFIDENTIAL clients.
         *  Without it Keycloak generates one the calling service cannot learn,
         *  and every client_credentials call returns 401. Must stay in step with
         *  zm-iam-service's ClientDeclaration: this record rejects unknown
         *  properties, so a field IAM knows and this does not fails the manifest
         *  before it is ever sent. */
        String secretEnvRef,
        List<@NotBlank String> serviceAccountRoles,
        @Valid List<ProtocolMapperDeclaration> protocolMappers
) {}
