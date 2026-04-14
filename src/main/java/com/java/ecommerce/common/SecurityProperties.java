package com.java.ecommerce.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.security")
@Validated
public class SecurityProperties {

    @NotBlank
    private String adminUsername;

    @NotBlank
    private String adminPassword;

    @NotBlank
    private String customerUsername;

    @NotBlank
    private String customerPassword;

    @NotBlank
    private String jwtSecret;

    @Positive
    private long jwtTtlSeconds = 900L;

    @Valid
    private final RateLimit rateLimit = new RateLimit();

    @Valid
    private final LoginThrottle loginThrottle = new LoginThrottle();

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public void setCustomerUsername(String customerUsername) {
        this.customerUsername = customerUsername;
    }

    public String getCustomerPassword() {
        return customerPassword;
    }

    public void setCustomerPassword(String customerPassword) {
        this.customerPassword = customerPassword;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtTtlSeconds() {
        return jwtTtlSeconds;
    }

    public void setJwtTtlSeconds(long jwtTtlSeconds) {
        this.jwtTtlSeconds = jwtTtlSeconds;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public LoginThrottle getLoginThrottle() {
        return loginThrottle;
    }

    public static class RateLimit {

        @Min(1)
        private int loginPerMinute = 10;

        @Min(1)
        private int writePerMinute = 120;

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getWritePerMinute() {
            return writePerMinute;
        }

        public void setWritePerMinute(int writePerMinute) {
            this.writePerMinute = writePerMinute;
        }
    }

    public static class LoginThrottle {

        @Min(1)
        private int maxFailures = 5;

        @Min(1)
        private long failureWindowSeconds = 300;

        @Min(1)
        private long blockDurationSeconds = 600;

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public long getFailureWindowSeconds() {
            return failureWindowSeconds;
        }

        public void setFailureWindowSeconds(long failureWindowSeconds) {
            this.failureWindowSeconds = failureWindowSeconds;
        }

        public long getBlockDurationSeconds() {
            return blockDurationSeconds;
        }

        public void setBlockDurationSeconds(long blockDurationSeconds) {
            this.blockDurationSeconds = blockDurationSeconds;
        }
    }
}
