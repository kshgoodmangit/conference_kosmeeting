package com.bjworld21.congress.dto;

public record CsrfTokenResponse(
        String headerName,
        String token
) {
}
