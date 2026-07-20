package zm.iam.provisioning.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import zm.iam.provisioning.client.response.ApplyResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * failure-mode branching. We use the real ManifestLoader (with a
 * classpath fixture) and a mocked client so we control the outcome.
 */
class ProvisioningStartupListenerTest {

    private final ManifestLoader loader = new ManifestLoader(new DefaultResourceLoader(), n -> null);

    @Test
    @DisplayName("Success → onReady returns without throwing")
    void successBranch() {
        IamProvisioningProperties props = props(IamProvisioningProperties.FailureMode.FAIL_FAST);
        IamProvisioningClient client = mock(IamProvisioningClient.class);
        when(client.apply(any())).thenReturn(new ApplyResponse("APPLIED", "tiny-svc", 1, List.of()));

        var listener = new ProvisioningStartupListener(props, loader, client);
        assertThatCode(listener::onReady).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FAIL_FAST + client throws → listener re-throws (Spring aborts startup)")
    void failFastRethrows() {
        IamProvisioningProperties props = props(IamProvisioningProperties.FailureMode.FAIL_FAST);
        IamProvisioningClient client = mock(IamProvisioningClient.class);
        when(client.apply(any())).thenThrow(
                new IamProvisioningClient.IamProvisioningException("nope", 500));

        var listener = new ProvisioningStartupListener(props, loader, client);
        assertThatThrownBy(listener::onReady)
                .isInstanceOf(IamProvisioningClient.IamProvisioningException.class);
    }

    @Test
    @DisplayName("LOG_AND_CONTINUE + client throws → listener swallows the exception")
    void logAndContinueSwallows() {
        IamProvisioningProperties props = props(IamProvisioningProperties.FailureMode.LOG_AND_CONTINUE);
        IamProvisioningClient client = mock(IamProvisioningClient.class);
        when(client.apply(any())).thenThrow(
                new IamProvisioningClient.IamProvisioningException("nope", 500));

        var listener = new ProvisioningStartupListener(props, loader, client);
        assertThatCode(listener::onReady).doesNotThrowAnyException();
    }

    private static IamProvisioningProperties props(IamProvisioningProperties.FailureMode mode) {
        IamProvisioningProperties p = new IamProvisioningProperties();
        p.setBaseUrl("http://ignored");
        p.setToken("t");
        p.setManifestPath("classpath:manifests/valid/minimal.yml");
        p.setFailureMode(mode);
        return p;
    }
}
