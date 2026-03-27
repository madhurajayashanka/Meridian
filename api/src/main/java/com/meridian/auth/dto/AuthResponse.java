package com.meridian.auth.dto;

import com.meridian.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for authentication responses (login, register, refresh).
 * Property 2: Login returns valid tokens with correct expiry
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("expires_in")
    private Integer expiresIn; // in seconds (15 minutes = 900)
    
    @JsonProperty("token_type")
    private String tokenType = "Bearer";

    private User user;
}
