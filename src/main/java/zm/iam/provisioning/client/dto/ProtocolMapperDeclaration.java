package zm.iam.provisioning.client.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Keycloak protocol mapper — copies a user attribute into a claim.
 *
 * <p>MUST stay in sync with the server-side DTO in zm-iam-service.
 * Contract tests (see {@code ManifestContractTest}) parse shared IAM-03
 * fixtures against these DTOs to catch drift.
 */
public record ProtocolMapperDeclaration(
        @NotBlank String name,
        @NotBlank String userAttribute,
        @NotBlank String claim,
        Boolean multivalued,
        String jsonType
) {}
