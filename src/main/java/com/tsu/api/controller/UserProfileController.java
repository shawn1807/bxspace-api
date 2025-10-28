package com.tsu.api.controller;

import com.tsu.common.data.ApiResponseWrapper;
import com.tsu.namespace.request.UpdateFullUserProfileRequest;
import com.tsu.namespace.dto.FullUserProfileResponse;
import com.tsu.namespace.dto.UserProfileResponse;
import com.tsu.namespace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class UserProfileController {

    private final UserService userService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<UserProfileResponse> get() {
        UserProfileResponse response = userService.getContextUserInfo();
        return ApiResponseWrapper.success(response, "Profile retrieved successfully");
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<UserProfileResponse> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfileResponse response = userService.updateLoginUserInfo(request);
        return ApiResponseWrapper.success(response, "Profile updated successfully");
    }

    @GetMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<UserProfileSettingsDto> getSettings() {
        UserProfileSettingsDto settings = userService.getUserSettings();
        return ApiResponseWrapper.success(settings, "Settings retrieved successfully");
    }

    @PutMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<UserProfileSettingsDto> updateSettings(@Valid @RequestBody UserProfileSettingsDto settings) {
        UserProfileSettingsDto updatedSettings = userService.updateUserSettings(settings);
        return ApiResponseWrapper.success(updatedSettings, "Settings updated successfully");
    }

    @GetMapping(value = "/full", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<FullUserProfileResponse> getFullProfile() {
        FullUserProfileResponse response = userService.getFullProfile();
        return ApiResponseWrapper.success(response, "Full profile retrieved successfully");
    }

    @PutMapping(value = "/full", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<FullUserProfileResponse> updateFullProfile(@Valid @RequestBody UpdateFullUserProfileRequest request) {
        FullUserProfileResponse response = userService.updateFullProfile(request);
        return ApiResponseWrapper.success(response, "Full profile updated successfully");
    }
}
