package com.java.ecommerce.customer;

import com.java.ecommerce.common.BusinessException;
import com.java.ecommerce.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final Optional<JavaMailSender> mailSender;
    private final boolean emailEnabled;
    private final String mailFrom;

    public CustomerService(
            CustomerRepository customerRepository,
            Optional<JavaMailSender> mailSender,
            @Value("${app.email.enabled:false}") boolean emailEnabled,
            @Value("${app.email.from:no-reply@ecommerce.local}") String mailFrom) {
        this.customerRepository = customerRepository;
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.mailFrom = mailFrom;
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    public Customer findByEmail(String email) {
        return customerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for current user"));
    }

    public String latestVerificationTokenByEmail(String email) {
        Customer customer = customerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for email: " + email));

        String token = customer.getEmailVerificationToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException("No verification token available for customer: " + email);
        }

        return token;
    }

    @Transactional
    public Customer createCustomer(String email, String fullName) {
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Customer email already exists: " + email);
        }

        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setFullName(fullName);
        customer.setEmailVerified(false);
        return customerRepository.save(customer);
    }

    @Transactional
    public void sendVerificationEmail(Long customerId) {
        Customer customer = findById(customerId);

        String token = UUID.randomUUID().toString();
        customer.setEmailVerificationToken(token);
        customer.setEmailVerificationSentAt(OffsetDateTime.now());

        String verifyBody = "Verify your ecommerce account using this token: " + token;
        if (emailEnabled && mailSender.isPresent()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(customer.getEmail());
            message.setSubject("Verify your ecommerce account");
            message.setText(verifyBody);
            try {
                mailSender.get().send(message);
            } catch (MailException ex) {
                LOG.warn("Failed to send verification email to {}. Token retained for manual verification.",
                        customer.getEmail(), ex);
            }
        } else {
            LOG.info("Email sending disabled. Verification token for {}: {}", customer.getEmail(), token);
        }
    }

    @Transactional
    public void verifyByToken(String token) {
        Customer customer = customerRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BusinessException("Invalid or expired verification token"));

        customer.setEmailVerified(true);
        customer.setEmailVerificationToken(null);
    }
}
