package com.bjworld21.conference.dto;

public record CsrfTokenResponse(
        String headerName,
        String token
) {
}
