package com.tsu.api.controller;

import com.tsu.auth.security.AppSecurityContextInitializer;
import com.tsu.common.data.ApiResponseWrapper;
import com.tsu.common.exception.UserException;
import com.tsu.namespace.api.UpdateUser;
import com.tsu.namespace.api.UserBase;
import com.tsu.namespace.api.UserProfile;
import com.tsu.namespace.dto.LoginUserInfoDto;
import com.tsu.namespace.request.UpdateUserProfileRequest;
import com.tsu.namespace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import static com.tsu.enums.BaseExceptionCode.INACTIVE_ACCOUNT;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class UserProfileController {

    private final UserService userService;
    private AppSecurityContextInitializer initializer;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<LoginUserInfoDto> get() {
        LoginUserInfoDto response = userService.getContextUserInfo();
        return ApiResponseWrapper.success(response, "Profile retrieved successfully");
    }


    @GetMapping(value = "/full", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<UserProfile> getSettings() {
        UserBase userBase = initializer.initializeAndVerify().getUser().orElseThrow(() -> new UserException(INACTIVE_ACCOUNT));
        return ApiResponseWrapper.success(userBase.toProfile(), "Settings retrieved successfully");
    }


    @PutMapping(value = "/full", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseWrapper<UserProfile> updateFullProfile(@Valid @RequestBody UpdateUserProfileRequest req) {
        UpdateUser update = new UpdateUser(req.getDisplayName(), req.getFirstName(), req.getLastName(), req.getImageURL(),
                req.getPhone(), req.getTimezoneId(), req.getLanguageTag(),
                req.getDatePattern(), req.getDatetimePattern(), req.getPreferences());
        UserProfile response = userService.updateContextUser(update);
        return ApiResponseWrapper.success(response, "Full profile updated successfully");
    }
}
