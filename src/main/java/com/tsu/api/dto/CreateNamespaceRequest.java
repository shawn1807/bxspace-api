package com.tsu.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateNamespaceRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private String logoImageUrl;
    private String lineId;
    private String instagramId;
    private String website;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^active$", message = "Status must be 'active'")
    private String status = "active";

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email must be valid")
    private String contactEmail;

    @NotBlank(message = "Access level is required")
    @Pattern(regexp = "^(open|approval|invitation)$", message = "Access level must be 'open', 'approval', or 'invitation'")
    private String accessLevel;


    @NotBlank(message = "Context path is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Context path must contain only lowercase letters, numbers, and hyphens")
    private String contextPath;


}