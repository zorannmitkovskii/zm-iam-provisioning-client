package zm.iam.provisioning.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** External IdP declaration. Secrets NEVER travel — only env-var refs. */
public record IdentityProviderDeclaration(
        @NotBlank String alias,
        @NotBlank String type,
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "envRef must be UPPER_SNAKE_CASE")
        String clientIdEnvRef,
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "envRef must be UPPER_SNAKE_CASE")
        String clientSecretEnvRef
) {}
