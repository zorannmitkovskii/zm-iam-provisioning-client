package zm.iam.provisioning.client.dto;

/**
 * Realm-level settings. Null = "let Keycloak default stand" (additive).
 *
 * <p>The two session lifespans are in seconds: how long a session survives
 * with nobody using it, and how long it can last at all. Together they decide
 * how long a refresh token keeps someone signed in.
 */
public record RealmSettings(
        Boolean loginWithEmailAllowed,
        Boolean registrationAllowed,
        Boolean resetPasswordAllowed,
        Boolean rememberMe,
        Boolean verifyEmail,
        Integer ssoSessionIdleTimeoutSeconds,
        Integer ssoSessionMaxLifespanSeconds
) {}
