package com.aiassistant.channel;

public interface AuthenticatedUserResolver {
    String resolve(String accessToken);
}
