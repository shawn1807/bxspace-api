package com.tsu.api.http.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceResponse {

    private String id;
    private String name;
    private String displayName;
    private String description;
    private String environment;
    private String team;
    private String project;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String owner;
    private String contactEmail;
    private String visibility;
    private String accessLevel;
    private String contextPath;


}