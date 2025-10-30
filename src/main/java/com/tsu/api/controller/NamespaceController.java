package com.tsu.api.controller;

import com.tsu.api.http.res.AvailabilityCheckResponse;
import com.tsu.api.http.req.CreateNamespaceRequest;
import com.tsu.api.dto.UpdateNamespaceRequest;
import com.tsu.api.service.NamespaceService;
import com.tsu.common.data.ApiResponseWrapper;
import com.tsu.namespace.dto.NamespaceDetailDto;
import com.tsu.namespace.dto.NamespaceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/namespaces")
@RequiredArgsConstructor
@Tag(name = "Namespace", description = "Namespace management API")
public class NamespaceController {

    private final NamespaceService namespaceService;

    @Operation(summary = "Get current user's namespaces", description = "Retrieve namespaces accessible by the current authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved namespaces"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<List<NamespaceDto>>> getAllNamespaces(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Getting current user's accessible namespaces with pagination: {}", pageable);
        List<NamespaceDto> namespaces = namespaceService.findAllNamespaces();
        return ResponseEntity.ok(ApiResponseWrapper.success(namespaces, "Namespaces retrieved successfully"));
    }


    @Operation(summary = "Get namespace by ID", description = "Retrieve a specific namespace by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved namespace"),
            @ApiResponse(responseCode = "404", description = "Namespace not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping(value = "/{uri}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<NamespaceDetailDto>> getNamespaceByPath(
            @Parameter(description = "Namespace Uri") @PathVariable String uri) {
        log.info("Getting namespace by uri: {}", uri);
        return namespaceService.findNamespaceByUri(uri)
                .map(namespace -> {
                    log.info("Found namespace: {} ({})", namespace.getName(), namespace.getId());
                    return ResponseEntity.ok(ApiResponseWrapper.success(namespace, "Namespace retrieved successfully"));
                })
                .orElseGet(() -> {
                    log.warn("Namespace not found: {}", uri);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponseWrapper.error("Namespace not found", "Namespace with context path " + uri + " does not exist"));
                });
    }


    @Operation(summary = "Check namespace URI availability", description = "Check if a URI is available for namespace creation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URI availability check completed"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping(value = "/check-uri/{uri}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<AvailabilityCheckResponse>> checkUri(
            @Parameter(description = "Namespace URI to check") @PathVariable String uri) {
        log.info("Checking URI availability: {}", uri);
        AvailabilityCheckResponse response = namespaceService.checkUriAvailability(uri);
        log.info("URI {} availability: {}", uri, response.isAvailable());
        return ResponseEntity.ok(ApiResponseWrapper.success(response, "URI availability checked successfully"));
    }


    @Operation(summary = "Get namespace by URI", description = "Retrieve a specific namespace by its URI")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved namespace"),
            @ApiResponse(responseCode = "404", description = "Namespace not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping(value = "/uri/{uri}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<NamespaceDetailDto>> getNamespaceByUri(
            @Parameter(description = "Namespace URI") @PathVariable String uri) {
        log.info("Getting namespace by URI: {}", uri);
        return namespaceService.findNamespaceByUri(uri)
                .map(namespace -> {
                    log.info("Found namespace: {} ({})", namespace.getName(), namespace.getId());
                    return ResponseEntity.ok(ApiResponseWrapper.success(namespace, "Namespace retrieved successfully"));
                })
                .orElseGet(() -> {
                    log.warn("Namespace not found: {}", uri);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponseWrapper.error("Namespace not found", "Namespace with URI " + uri + " does not exist"));
                });
    }

    @Operation(summary = "Create namespace", description = "Create a new namespace")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Namespace created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Namespace already exists"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<NamespaceDetailDto>> createNamespace(
            @Parameter(description = "Namespace creation request") @Valid @RequestBody CreateNamespaceRequest request) {
        log.info("Creating namespace: {} with context path: {}",
                request.getName(), request.getContextPath());

        try {
            NamespaceDetailDto namespace = namespaceService.createNamespace(request);
            log.info("Created namespace: {} ({})", namespace.getName(), namespace.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseWrapper.success(namespace, "Namespace created successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create namespace: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseWrapper.error("Conflict", e.getMessage()));
        }
    }


    @Operation(summary = "Update namespace", description = "Update an existing namespace")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Namespace updated successfully"),
            @ApiResponse(responseCode = "404", description = "Namespace not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Conflict with existing data"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<NamespaceDetailDto>> updateNamespace(
            @Parameter(description = "Namespace ID") @PathVariable String id,
            @Parameter(description = "Namespace update request") @Valid @RequestBody UpdateNamespaceRequest request) {
        log.info("Updating namespace: {}", id);

        try {
            return namespaceService.updateNamespace(id, request)
                    .map(namespace -> {
                        log.info("Updated namespace: {} ({})", namespace.getName(), namespace.getId());
                        return ResponseEntity.ok(ApiResponseWrapper.success(namespace, "Namespace updated successfully"));
                    })
                    .orElseGet(() -> {
                        log.warn("Namespace not found for update: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponseWrapper.error("Namespace not found", "Namespace with ID " + id + " does not exist"));
                    });
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update namespace {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseWrapper.error("Conflict", e.getMessage()));
        }
    }

    @Operation(summary = "Patch namespace", description = "Partially update an existing namespace")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Namespace updated successfully"),
            @ApiResponse(responseCode = "404", description = "Namespace not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Conflict with existing data"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponseWrapper<NamespaceDetailDto>> patchNamespace(
            @Parameter(description = "Namespace ID") @PathVariable String id,
            @Parameter(description = "Namespace patch request") @RequestBody UpdateNamespaceRequest request) {
        log.info("Patching namespace: {}", id);

        try {
            return namespaceService.updateNamespace(id, request)
                    .map(namespace -> {
                        log.info("Patched namespace: {} ({})", namespace.getName(), namespace.getId());
                        return ResponseEntity.ok(ApiResponseWrapper.success(namespace, "Namespace updated successfully"));
                    })
                    .orElseGet(() -> {
                        log.warn("Namespace not found for patch: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponseWrapper.error("Namespace not found", "Namespace with ID " + id + " does not exist"));
                    });
        } catch (IllegalArgumentException e) {
            log.warn("Failed to patch namespace {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseWrapper.error("Conflict", e.getMessage()));
        }
    }
}
