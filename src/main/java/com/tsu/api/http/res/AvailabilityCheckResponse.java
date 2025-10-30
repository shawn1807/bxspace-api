package com.tsu.api.http.res;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AvailabilityCheckResponse {

    private boolean avb; //availability
    private String sug; //sug

}
