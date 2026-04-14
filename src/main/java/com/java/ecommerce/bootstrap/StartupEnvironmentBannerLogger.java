package com.java.ecommerce.bootstrap;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@Lazy(false)
public class StartupEnvironmentBannerLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupEnvironmentBannerLogger.class);

    private final Environment environment;
    private final DataSource dataSource;

    public StartupEnvironmentBannerLogger(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void logStartupEnvironment() {
        String activeProfiles = resolveActiveProfiles();
        String datasourceUrl = resolveDatasourceUrl();
        String datasourceMode = resolveDatasourceMode(datasourceUrl);
        String profileHint = resolveProfileHint(datasourceMode);

        log.info(
                """

                        =======================================================
                        Startup Environment
                        Active profiles : {}
                        Datasource mode : {}
                        Profile hint    : {}
                        Datasource URL  : {}
                        =======================================================
                        """,
                activeProfiles,
                datasourceMode,
                profileHint,
                datasourceUrl);
    }

    private String resolveActiveProfiles() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            active = environment.getDefaultProfiles();
        }

        String joined = Arrays.stream(active)
                .filter(profile -> profile != null && !profile.isBlank())
                .collect(Collectors.joining(", "));

        return joined.isBlank() ? "default" : joined;
    }

    private String resolveDatasourceUrl() {
        String configuredUrl = environment.getProperty("spring.datasource.url");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }

        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (url != null && !url.isBlank()) {
                return url;
            }
        } catch (SQLException ex) {
            log.debug("Could not resolve datasource URL from JDBC metadata.", ex);
        }

        return "unknown";
    }

    private String resolveDatasourceMode(String datasourceUrl) {
        if (datasourceUrl != null) {
            String normalized = datasourceUrl.toLowerCase(Locale.ROOT);
            if (normalized.contains(":h2:")) {
                return "H2";
            }
            if (normalized.contains(":postgresql:")) {
                return "Postgres";
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null && !productName.isBlank()) {
                return productName;
            }
        } catch (SQLException ex) {
            log.debug("Could not resolve datasource mode from JDBC metadata.", ex);
        }

        return "unknown";
    }

    private String resolveProfileHint(String datasourceMode) {
        String normalized = datasourceMode.toLowerCase(Locale.ROOT);
        if (normalized.contains("h2")) {
            return "Demo mode (H2)";
        }
        if (normalized.contains("postgres")) {
            return "Portfolio mode (Postgres)";
        }
        return "Custom mode";
    }
}
