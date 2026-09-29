package com.foliox.auth.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuthError(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {
}
