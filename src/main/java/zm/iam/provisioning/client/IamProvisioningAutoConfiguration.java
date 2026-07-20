package zm.iam.provisioning.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-config entry point. All beans are gated on
 * {@code iam.provisioning.enabled=true} (default). When disabled the
 * whole configuration class is skipped — zero beans registered, zero
 * HTTP calls, one DEBUG line confirming the state.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(IamProvisioningProperties.class)
@ConditionalOnProperty(prefix = "iam.provisioning", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class IamProvisioningAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ManifestLoader manifestLoader() {
        return new ManifestLoader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "iamProvisioningObjectMapper")
    public ObjectMapper iamProvisioningObjectMapper() {
        // Dedicated mapper — NON_NULL so nulls don't clutter the request
        // body. defaultCandidate = false would be nice but @Bean names are
        // enough scoping here.
        ObjectMapper m = new ObjectMapper();
        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return m;
    }

    @Bean
    @ConditionalOnMissingBean
    public IamProvisioningClient iamProvisioningClient(IamProvisioningProperties props,
                                                      ObjectMapper iamProvisioningObjectMapper) {
        return new IamProvisioningClient(props, iamProvisioningObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProvisioningStartupListener provisioningStartupListener(
            IamProvisioningProperties props,
            ManifestLoader loader,
            IamProvisioningClient client) {
        log.debug("[IamProvisioningClient] auto-configured — will apply manifest on ApplicationReadyEvent");
        return new ProvisioningStartupListener(props, loader, client);
    }
}
