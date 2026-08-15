package com.greeniot.greensense.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greeniot.greensense.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Makes Spring Security speak the same envelope as the rest of the API, and — more
 * importantly — makes it distinguish the two failures a client must handle differently.
 *
 * <p>Out of the box a request with no credentials at all is rejected with <b>403</b>,
 * identical to a request from someone who is signed in but not allowed. A browser client
 * cannot then tell "my access token expired, silently refresh and retry" from "this is not
 * mine, show an error" — so it either refreshes on every denial or never refreshes at all.
 * Splitting them into 401 and 403 is what makes the silent-refresh retry correct.
 */
@Component
@RequiredArgsConstructor
public class RestAuthEntryPoints {

    private final ObjectMapper objectMapper;

    /** No credentials, or a token that did not verify → 401, retry after refreshing. */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                write(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "Authentication required");
    }

    /** Valid identity, insufficient rights → 403, refreshing will not help. */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                write(response, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "You do not have access to this resource");
    }

    private void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.fail(new ApiResponse.ApiError(code, message, null)));
    }
}
