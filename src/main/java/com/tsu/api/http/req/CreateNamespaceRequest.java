package com.tsu.api.http.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateNamespaceRequest {

    @NotBlank(message = "Name is required")
    private String nme;
    private String desc;
    private String logo;
    private String line;
    private String igId;
    private String website;
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^active$", message = "Status must be 'active'")
    private String status = "active";

    @NotBlank(message = "Owner is required")
    private String own;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email must be valid")
    private String eml;

    @NotBlank(message = "Access level is required")
    @Pattern(regexp = "^(open|approval|invitation)$", message = "Access level must be 'open', 'approval', or 'invitation'")
    private String acc;

    @NotBlank(message = "uri is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "uri must contain only lowercase letters, numbers, and hyphens")
    private String uri;


}