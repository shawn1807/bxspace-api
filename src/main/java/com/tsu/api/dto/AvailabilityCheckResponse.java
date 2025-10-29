package com.tsu.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AvailabilityCheckResponse {

    private boolean available;
    private String suggestion;

}
