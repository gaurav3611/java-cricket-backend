package com.sswingg.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that needs a signed-in user, like the Flask @require_auth decorator.
 * The caller's Firebase uid is then available as the request attribute {@link AuthInterceptor#UID}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {
}
