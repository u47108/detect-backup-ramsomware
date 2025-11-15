package com.example.detectbackupransomware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad Cuenta Bancaria con datos sensibles financieros.
 */
@Entity
@Table(name = "bank_accounts", indexes = {
    @Index(name = "idx_bank_account_user", columnList = "user_id"),
    @Index(name = "idx_bank_account_number", columnList = "account_number"),
    @Index(name = "idx_bank_account_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "routing_number", length = 50)
    private String routingNumber;

    @Column(name = "account_type", length = 50)
    private String accountType;

    @Column(name = "credit_card_number", length = 100)
    private String creditCardNumber;

    @Column(name = "cvv", length = 10)
    private String cvv;

    @Column(name = "expiry_date", length = 20)
    private String expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

