package com.tsu.api.service;

import com.tsu.api.http.res.AvailabilityCheckResponse;
import com.tsu.api.http.req.CreateNamespaceRequest;
import com.tsu.api.dto.UpdateNamespaceRequest;
import com.tsu.auth.api.AccessLevel;
import com.tsu.auth.security.AppSecurityContext;
import com.tsu.auth.security.AppSecurityContextInitializer;
import com.tsu.auth.security.NamespaceContext;
import com.tsu.common.utils.ParamValidator;
import com.tsu.common.val.UserVal;
import com.tsu.common.vo.Email;
import com.tsu.common.vo.Text;
import com.tsu.entry.api.AclMode;
import com.tsu.entry.api.EntryBucket;
import com.tsu.enums.BaseParamName;
import com.tsu.namespace.api.Namespace;
import com.tsu.namespace.api.UserBase;
import com.tsu.namespace.dto.NamespaceDetailDto;
import com.tsu.namespace.dto.NamespaceDto;
import com.tsu.namespace.service.AppService;
import com.tsu.namespace.service.UserService;
import com.tsu.namespace.val.NamespaceUserMvVal;
import com.tsu.namespace.val.NamespaceVal;
import com.tsu.workspace.request.AddNamespace;
import com.tsu.workspace.request.UserFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NamespaceService {

    private final AppSecurityContextInitializer securityContextInitializer;
    private final AppService appService;
    private final UserService userService;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<NamespaceDto> findAllNamespaces() {
        AppSecurityContext context = securityContextInitializer.initializeAndVerify();
        // Get namespaces from UserBase context instead of all namespaces
        return appService.findJoinedNamespaces(context.getPrincipal())
                .map(this::toNamespaceResponse)
                .toList();
    }


    public Optional<NamespaceDetailDto> findNamespaceById(String id) {
        UUID namespaceId = ParamValidator.convertAndCheckUUID(id, BaseParamName.NAMESPACE);
        return appService.findNamespaceContextById(namespaceId)
                .map(NamespaceContext::getNamespace)
                .map(this::toNamespaceDetailResponse);
    }


    public Optional<NamespaceDetailDto> findNamespaceByUri(String uri) {
        return appService.findNamespaceContextByUri(Text.of(uri))
                .map(NamespaceContext::getNamespace)
                .map(this::toNamespaceDetailResponse);
    }

    public AvailabilityCheckResponse checkUriAvailability(String uri) {
        log.info("Checking URI availability: {}", uri);
        // Check if URI is already taken
        return appService.findNamespaceIdByName(Text.of(uri))
                .map(id -> AvailabilityCheckResponse.builder()
                        .available(false)
                        .suggestion(generateUriSuggestions(uri))
                        .build())
                .orElseGet(() -> AvailabilityCheckResponse.builder()
                        .available(true)
                        .build());
    }

    private String generateUriSuggestions(String baseUri) {
        // Clean the base URI first
        String cleanUri = baseUri.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        //try 10 times
        return IntStream.range(0, 10)
                .map(i -> 1000 + secureRandom.nextInt(9000))
                .boxed()
                .map(randomNum -> (cleanUri + "-" + randomNum))
                .filter(uri -> appService.findNamespaceIdByUri(Text.of(uri)).isEmpty())
                .findFirst()
                .orElse(null);

    }

    @Transactional
    public NamespaceDetailDto createNamespace(CreateNamespaceRequest request) {
        AppSecurityContext appSecurityContext = securityContextInitializer.initializeAndVerify();
        log.info("Creating namespace: {} with context path: {}",
                request.getName(), request.getContextPath());

        // Check if namespace with context path already exists
        if (appService.findNamespaceIdByUri(Text.of(request.getContextPath())).isPresent()) {
            throw new IllegalArgumentException("Namespace with context path '" + request.getContextPath() + "' already exists");
        }

        // Use context path as the bucket name for now (could be generated differently)
        EntryBucket bucket = bucketProvider.createBucket(request.getContextPath(), AclMode.FULL);

        // Build background image URL from image info if provided
        String backgroundImageUrl = null;
        NamespaceProps namespaceProps = null;
        if (request.getImage() != null) {
            backgroundImageUrl = request.getImage().getUrl();
            // Create namespace props with background image properties (excluding URL)
            namespaceProps = NamespaceProps.builder()
                    .backgroundImage(NamespaceProps.BackgroundImage.builder()
                            .position(Position.builder()
                                    .x(request.getImage().getPosition().getX())
                                    .y(request.getImage().getPosition().getY())
                                    .build())
                            .scale(request.getImage().getScale())
                            .rotation(request.getImage().getRotation())
                            .build())
                    .socialId(NamespaceProps.SocialId.builder()
                            .instagramId(request.getInstagramId())
                            .lineId(request.getLineId())
                            .build())
                    .build();
        }

        // Create AddNamespace request object with available fields
        AddNamespace addNamespace = AddNamespace.builder()
                .name(Text.of(request.getName()))
                .description(Text.of(request.getDescription()))
                .uri(request.getContextPath())
                .website(request.getWebsite())
                .contactEmail(Email.of(request.getContactEmail()))
                .bucket(bucket.getName())
                .logoImageUrl(request.getLogoImageUrl())
                .backgroundImageUrl(request.getImage().getUrl())
                .accessLevel(AccessLevel.valueOf(request.getAccessLevel()))
                .build();
        NamespaceContext context = appService.post(addNamespace, namespaceProps);
        return toNamespaceDetailResponse(context.getNamespace());
    }


    @Transactional
    public Optional<NamespaceDetailDto> updateNamespace(String id, UpdateNamespaceRequest request) {
        log.info("Updating namespace: {} with request: {}", id, request);
        UUID namespaceId = ParamValidator.convertAndCheckUUID(id, BaseParamName.NAMESPACE);

        return appService.findNamespaceContextById(namespaceId)
                .map(context -> {
                    boolean updated = false;
                    Namespace namespace = context.getNamespace();
                    NamespaceVal val = namespace.getValue();
                    if (request.getName() != null && !request.getName().equals(val.name())) {
                        // Check if new name is already taken
                        if (appService.findNamespaceIdByName(Text.of(request.getName())).isPresent()) {
                            throw new IllegalArgumentException("Namespace with name '" + request.getName() + "' already exists");
                        }
                        namespace.setName(Text.of(request.getName()));
                    }
                    if (request.getProps() != null) {
                        namespace.setProps(request.getProps());
                        updated = true;
                    }
                    if (updated) {
                        log.info("Updated namespace: {} ({})", namespace.getValue().name(), namespace.getValue().id());
                    }
                    return toNamespaceDetailResponse(namespace);
                });
    }
    private NamespaceDto toNamespaceResponse(NamespaceVal val){
        String status = val.active() ? "active" : "inactive";
        log.debug("Converting namespace to response: {}", val);

        // Get owner display name
        String ownerName = userService.findUser(val.owner())
                .map(UserBase::getValue)
                .map(UserVal::displayName)
                .orElse(null);

        // Map access level to visibility
        String visibility = mapAccessLevelToVisibility(val.accessLevel());
        return NamespaceDto.builder()
                .id(val.id().toString())
                .name(val.name())
                .displayName(val.name()) // Use name as display name for now
                .description(val.description())
                .status(status)
                .environment("production") // Default environment
                .createdAt(val.createDate())
                .updatedAt(val.modifiedDate())
                .contactEmail(val.supportEmail())
                .visibility(visibility)
                .accessLevel(val.accessLevel().name().toLowerCase())
                .contextPath(val.uri())
                .build();
    }

    private NamespaceDetailDto toNamespaceDetailResponse(Namespace namespace) {
        NamespaceVal val = namespace.getValue();
        NamespaceProps props = namespace.getProps(NamespaceProps.class)
                .orElseGet(()-> NamespaceProps.builder().build());
        // Map status based on active flag
        String status = val.active() ? "active" : "inactive";
        log.debug("Converting namespace to response: {}", val);
        // Get owner display name
        String ownerName = userService.findUser(val.owner())
                .map(UserBase::getValue)
                .map(UserVal::displayName)
                .orElse(null);

        // Map access level to visibility
        String visibility = mapAccessLevelToVisibility(val.accessLevel());

        // Build default resource quotas
        NamespaceDetailDto.ResourceQuotas resourceQuotas = NamespaceDetailDto.ResourceQuotas.builder()
                .enabled(true)
                .limits(NamespaceDetailDto.ResourceQuotas.ResourceLimits.builder()
                        .cpu("2")
                        .memory("4Gi")
                        .storage("50Gi")
                        .pods(30)
                        .build())
                .build();

        ImageInfo image = new ImageInfo();
        image.setUrl(val.backgroundImageUrl());
        Optional.ofNullable(props.getBackgroundImage())
                        .ifPresent(img->{
                            image.setPosition(img.getPosition());
                            image.setRotation(img.getRotation());
                            image.setScale(img.getScale());
                        });

        // Build the response with all available fields
        return NamespaceDetailDto.builder()
                .id(val.id().toString())
                .name(val.name())
                .displayName(val.name()) // Use name as display name for now
                .description(val.description())
                .status(status)
                .createdAt(val.createDate())
                .updatedAt(val.modifiedDate())
                .owner(ownerName)
                .contactEmail(val.supportEmail())
                .visibility(visibility)
                .accessLevel(val.accessLevel().name().toLowerCase())
                .contextPath(val.uri())
                .category(null) // TODO: Get from namespace properties when available
                .memberCount(null) // TODO: Count members when member API is available
                .image(image)
                .resourceQuotas(resourceQuotas)
                .build();
    }

    /**
     * Map AccessLevel enum to visibility string for API response.
     */
    private String mapAccessLevelToVisibility(AccessLevel accessLevel) {
        return switch (accessLevel) {
            case open -> "public";
            case invitation -> "team";
            case approval -> "private";
            default -> "private";
        };
    }

    // ==================== Namespace User Methods ====================

    /**
     * Search/query namespace users using filter criteria and pagination.
     * Uses the Namespace.queryUsers method which leverages the namespace_user_mv materialized view.
     */
    public Page<NamespaceUserMvVal> queryNamespaceUsers(String namespaceId, UserFilter filter, Pageable pageable) {
        log.info("Querying namespace users for namespace: {} with filter: {}", namespaceId, filter);
        UUID nsId = ParamValidator.convertAndCheckUUID(namespaceId, BaseParamName.NAMESPACE);

        return appService.findNamespaceContextById(nsId)
                .map(context -> {
                    Namespace namespace = context.getNamespace();
                    // Ensure the filter includes the namespace ID
                    UserFilter effectiveFilter = filter != null ? filter : UserFilter.builder().build();
                    if (effectiveFilter.getNamespaceId() == null) {
                        effectiveFilter.setNamespaceId(nsId);
                    }
                    return namespace.queryUsers(effectiveFilter, pageable);
                })
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + namespaceId));
    }



}
