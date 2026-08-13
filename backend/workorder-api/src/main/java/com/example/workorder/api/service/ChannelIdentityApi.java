package com.example.workorder.api.service;

import com.example.workorder.api.dto.*;

public interface ChannelIdentityApi {
    BindingChallengeResponse createChallenge(String authorization);
    ChannelBindingResult confirm(String serviceKey, ConfirmBindingRequest request);
    ChannelBindingResult find(String serviceKey, FeishuIdentityRequest request);
    void unbind(String authorization);
    ChannelTokenResponse exchange(String serviceKey, ChannelTokenRequest request);
}
