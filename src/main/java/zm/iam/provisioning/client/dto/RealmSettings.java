package zm.iam.provisioning.client.dto;

/** Realm-level toggles. Null = "let Keycloak default stand" (additive). */
public record RealmSettings(
        Boolean loginWithEmailAllowed,
        Boolean registrationAllowed,
        Boolean resetPasswordAllowed,
        Boolean rememberMe,
        Boolean verifyEmail
) {}
