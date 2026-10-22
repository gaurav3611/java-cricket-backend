package com.sswingg.api.profile;

import java.util.Map;

import com.sswingg.api.auth.AuthInterceptor;
import com.sswingg.api.auth.RequireAuth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @RequireAuth
    @GetMapping
    public Map<String, Object> get(@RequestAttribute(AuthInterceptor.UID) String uid) {
        return profileService.getProfile(uid);
    }

    @RequireAuth
    @PutMapping
    public Map<String, Object> update(
            @RequestAttribute(AuthInterceptor.UID) String uid,
            @RequestBody(required = false) Map<String, Object> body) {
        return Map.of("status", profileService.updateProfile(uid, body));
    }
}
