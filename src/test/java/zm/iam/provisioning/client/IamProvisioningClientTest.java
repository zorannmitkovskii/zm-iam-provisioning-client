package zm.iam.provisioning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zm.iam.provisioning.client.dto.ClientDeclaration;
import zm.iam.provisioning.client.dto.ClientType;
import zm.iam.provisioning.client.dto.RealmDeclaration;
import zm.iam.provisioning.client.dto.ServiceProvisioningManifest;

import java.net.http.HttpClient;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry state machine tests. Uses WireMock scenarios to sequence
 * "fail 3× then success" and a no-sleep {@code RetrySleeper} so JUnit
 * doesn't wait 15+ seconds for the real backoff to complete.
 */
class IamProvisioningClientTest {

    private WireMockServer wm;
    private IamProvisioningProperties props;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(wireMockConfig().dynamicPort());
        wm.start();
        props = new IamProvisioningProperties();
        props.setBaseUrl("http://localhost:" + wm.port());
        props.setToken("test-plaintext-token");
        props.setRetryMaxWindowSeconds(30);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    @DisplayName("2xx on first attempt → single POST, returns parsed response")
    void firstAttemptSucceeds() {
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":true,"message":null,"data":{
                                  "status":"APPLIED","serviceId":"svc","appliedVersion":1,"changes":[]}}
                                """)));

        var response = newClient().apply(manifest());
        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.appliedVersion()).isEqualTo(1);
        wm.verify(1, postRequestedFor(urlPathEqualTo("/provisioning/manifests")));
    }

    @Test
    @DisplayName("Fail 3× with 503 then succeed → 4 POSTs total, no real waits")
    void retriesOnFiveHundredThenSucceeds() {
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("a"));
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .inScenario("retry").whenScenarioStateIs("a")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("b"));
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .inScenario("retry").whenScenarioStateIs("b")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("c"));
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .inScenario("retry").whenScenarioStateIs("c")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":true,"data":{"status":"NOOP","serviceId":"svc","appliedVersion":1,"changes":[]}}
                                """)));

        var response = newClient().apply(manifest());
        assertThat(response.status()).isEqualTo("NOOP");
        wm.verify(4, postRequestedFor(urlPathEqualTo("/provisioning/manifests")));
    }

    @Test
    @DisplayName("4xx → immediate failure, no retry")
    void fourHundredFailsFast() {
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .willReturn(aResponse().withStatus(400)
                        .withBody("{\"success\":false,\"message\":\"Validation failed\"}")));

        assertThatThrownBy(() -> newClient().apply(manifest()))
                .isInstanceOf(IamProvisioningClient.IamProvisioningException.class)
                .hasMessageContaining("400");
        wm.verify(1, postRequestedFor(urlPathEqualTo("/provisioning/manifests")));
    }

    @Test
    @DisplayName("Retry window exhausted → IamProvisioningException with last error")
    void windowExhausted() {
        wm.stubFor(post(urlPathEqualTo("/provisioning/manifests"))
                .willReturn(aResponse().withStatus(503)));
        // Very short window so the loop exits quickly.
        props.setRetryMaxWindowSeconds(1);

        assertThatThrownBy(() -> newClient().apply(manifest()))
                .isInstanceOf(IamProvisioningClient.IamProvisioningException.class)
                .hasMessageContaining("failed after");
    }

    private IamProvisioningClient newClient() {
        HttpClient http = HttpClient.newHttpClient();
        return new IamProvisioningClient(props, new ObjectMapper(), http, ms -> { /* no-op sleeper */ });
    }

    private static ServiceProvisioningManifest manifest() {
        return new ServiceProvisioningManifest("svc", 1, List.of(
                new RealmDeclaration("app", null, List.of(
                        new ClientDeclaration("app-fe", ClientType.PUBLIC, null,
                                List.of("https://x.mk/*"), null, null, null, null, null)),
                        null, null, null)));
    }
}
