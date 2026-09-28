package zm.iam.provisioning.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestLoaderTest {

    @Test
    @DisplayName("Loads + validates a minimal manifest from the classpath")
    void loadsMinimalManifest() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        var manifest = loader.load("classpath:manifests/valid/minimal.yml");
        assertThat(manifest.serviceId()).isEqualTo("tiny-svc");
        assertThat(manifest.manifestVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("${ENV_VAR} interpolation — value substituted before parse")
    void interpolatesEnvVar() {
        Function<String, String> env = Map.of("APP_DOMAIN", "test.example.com")::get;
        var loader = new ManifestLoader(new DefaultResourceLoader(), env);
        var manifest = loader.load("classpath:manifests/with-env-interp.yml");
        assertThat(manifest.realms().get(0).clients().get(0).redirectUris())
                .containsExactly("https://test.example.com/*");
    }

    @Test
    @DisplayName("Missing ${ENV_VAR} → MissingEnvVarException with the var name")
    void missingEnvVarFails() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        assertThatThrownBy(() -> loader.load("classpath:manifests/with-env-interp.yml"))
                .isInstanceOf(ManifestLoader.MissingEnvVarException.class)
                .hasMessageContaining("APP_DOMAIN");
    }

    @Test
    @DisplayName("Missing manifest file → IllegalStateException with the path")
    void missingFileFails() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        assertThatThrownBy(() -> loader.load("classpath:manifests/does-not-exist.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist.yml");
    }

    @Test
    @DisplayName("Client-side validation catches an invalid manifest before send")
    void validationCatchesInvalid() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        assertThatThrownBy(() -> loader.load("classpath:manifests/invalid/serviceId-uppercase.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serviceId");
    }

    @Test
    @DisplayName("Contract: zm-services with foreign client rejected client-side")
    void zmServicesScopeEnforcedClientSide() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        assertThatThrownBy(() -> loader.load("classpath:manifests/invalid/zm-services-foreign-client.yml"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Full Ivy manifest parses + validates identically to the server")
    void ivyManifestParses() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        var m = loader.load("classpath:manifests/valid/ivy.yml");
        assertThat(m.realms()).hasSize(2);
        assertThat(m.realms().get(1).name()).isEqualTo("zm-services");
    }

    @Test
    @DisplayName("Realm session lifespans parse — unknown-field strictness would otherwise refuse them")
    void sessionLifespansParse() {
        var loader = new ManifestLoader(new DefaultResourceLoader(), noEnv());
        var settings = loader.load("classpath:manifests/valid/session-lifespans.yml").realms().get(0).settings();
        assertThat(settings.ssoSessionIdleTimeoutSeconds()).isEqualTo(604800);
        assertThat(settings.ssoSessionMaxLifespanSeconds()).isEqualTo(2592000);
    }

    private static Function<String, String> noEnv() {
        return name -> null;
    }
}
