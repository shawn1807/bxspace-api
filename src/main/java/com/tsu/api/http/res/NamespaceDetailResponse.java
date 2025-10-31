package com.tsu.api.http.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceDetailResponse {

    private String id;
    private String name;
    private String displayName;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> labels;

    private String owner;
    private String contactEmail;
    private String visibility;
    private String accessLevel;
    private String contextPath;

    private String category;
    private Integer memberCount;


}