package com.swinglab.api.auth;

import com.swinglab.api.error.ApiException;
import com.swinglab.api.firebase.FirebaseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Checks the Authorization header on methods marked with {@link RequireAuth}. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Request attribute that holds the caller's uid. */
    public static final String UID = "uid";

    /** The uid every caller gets while the API runs in demo mode. */
    public static final String DEMO_UID = "demo-user";

    private static final String BEARER = "Bearer ";

    private final FirebaseContext firebase;
    private final TokenVerifier tokenVerifier;

    public AuthInterceptor(FirebaseContext firebase, TokenVerifier tokenVerifier) {
        this.firebase = firebase;
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(RequireAuth.class)) {
            return true;
        }
        if (firebase.isDemoMode()) {
            request.setAttribute(UID, DEMO_UID);
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing authorization token");
        }
        String uid = tokenVerifier.verify(header.substring(BEARER.length()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
        request.setAttribute(UID, uid);
        return true;
    }
}
