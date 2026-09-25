package zm.iam.provisioning.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import zm.iam.provisioning.client.dto.ServiceProvisioningManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the YAML manifest from the classpath, resolves any
 * {@code ${ENV_VAR}} placeholders against the process environment, and
 * validates it with jakarta.validation BEFORE returning. A failed
 * validation here means the service fails startup with a clear message
 * instead of getting a 400 from IAM.
 *
 * <p>Placeholder syntax: {@code ${ENV_VAR}} → value of {@code ENV_VAR}
 * from {@link System#getenv}. Missing var → {@link MissingEnvVarException}.
 */
@Slf4j
public class ManifestLoader {

    /** {@code ${...}} — dollar + curly braces around anything not curly. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper yaml;
    private final ResourceLoader resourceLoader;
    private final Function<String, String> envSupplier;
    private final Validator validator;

    public ManifestLoader() {
        this(new DefaultResourceLoader(), System::getenv);
    }

    public ManifestLoader(ResourceLoader resourceLoader,
                          Function<String, String> envSupplier) {
        this.resourceLoader = resourceLoader;
        this.envSupplier = envSupplier;
        // A manifest with a key we do not know is a typo, not a new feature,
        // so unknown properties stay fatal. Built rather than configured —
        // Jackson 3's mapper is immutable.
        this.yaml = YAMLMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .build();
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    public ServiceProvisioningManifest load(String path) {
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Manifest not found at " + path
                    + " — put iam-manifest.yml on the classpath or set iam.provisioning.manifest-path");
        }

        String raw;
        try (var in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read manifest at " + path, e);
        }

        String resolved = interpolate(raw);

        ServiceProvisioningManifest manifest;
        try {
            manifest = yaml.readValue(resolved, ServiceProvisioningManifest.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Manifest at " + path
                    + " is not valid YAML for ServiceProvisioningManifest: " + e.getMessage(), e);
        }

        Set<ConstraintViolation<ServiceProvisioningManifest>> violations = validator.validate(manifest);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new IllegalStateException(
                    "Manifest at " + path + " failed client-side validation: " + detail);
        }

        log.info("[IamProvisioningClient] Loaded manifest serviceId='{}' version={} realms={}",
                manifest.serviceId(), manifest.manifestVersion(), manifest.realms().size());
        return manifest;
    }

    private String interpolate(String raw) {
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String var = m.group(1);
            String value = envSupplier.apply(var);
            if (value == null) {
                throw new MissingEnvVarException(
                        "Manifest references ${" + var + "} but the env var is not set");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Thrown when a manifest placeholder points at an env var that
     *  isn't defined. Bubbles up so the fail-fast mode kills the app
     *  cleanly with a message the operator can act on. */
    public static class MissingEnvVarException extends RuntimeException {
        public MissingEnvVarException(String msg) { super(msg); }
    }
}
