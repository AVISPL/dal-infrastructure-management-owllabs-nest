package com.avispl.symphony.dal.communicator.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Authorization {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("expires_in")
    private Long expiresIn;
    @JsonProperty("token_type")
    private String tokenType;

    private Long authorizationTimestamp;
    Authorization() {
        authorizationTimestamp = System.currentTimeMillis()/1000;
    }

    public boolean needsUpdate() {
        // Considering the update is required 5 minutes before it actually expires
        return ((System.currentTimeMillis()/1000) - (expiresIn - 300)) > authorizationTimestamp;
    }
    /**
     * Retrieves {@link #accessToken}
     *
     * @return value of {@link #accessToken}
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Sets {@link #accessToken} value
     *
     * @param accessToken new value of {@link #accessToken}
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Retrieves {@link #expiresIn}
     *
     * @return value of {@link #expiresIn}
     */
    public Long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Sets {@link #expiresIn} value
     *
     * @param expiresIn new value of {@link #expiresIn}
     */
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * Retrieves {@link #tokenType}
     *
     * @return value of {@link #tokenType}
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Sets {@link #tokenType} value
     *
     * @param tokenType new value of {@link #tokenType}
     */
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
