package com.java.ecommerce.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("dev-mail-sandbox")
@RequestMapping("/api/dev/mail-sandbox")
public class DevMailSandboxController {

    private final CustomerService customerService;

    public DevMailSandboxController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/customers/verification-token")
    public VerificationTokenResponse latestVerificationToken(
            @RequestParam @NotBlank @Email String email) {
        String token = customerService.latestVerificationTokenByEmail(email);
        return new VerificationTokenResponse(email, token);
    }

    public record VerificationTokenResponse(String email, String token) {
    }
}
