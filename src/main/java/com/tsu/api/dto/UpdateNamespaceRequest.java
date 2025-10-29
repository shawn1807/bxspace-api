package com.tsu.api.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateNamespaceRequest {
    private String name;

    @Email(message = "Contact email must be valid")
    private String contactEmail;

    private Boolean active;

    private LocalDate expirationDate;

    private Object props;
}