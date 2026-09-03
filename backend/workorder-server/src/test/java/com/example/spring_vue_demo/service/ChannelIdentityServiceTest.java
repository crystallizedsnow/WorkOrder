package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.mapper.ChannelBindingChallengeMapper;
import com.example.spring_vue_demo.mapper.ExternalIdentityBindingMapper;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.utils.TokenUtil;
import com.example.workorder.api.dto.BatchResolveBindingRequest;
import com.example.workorder.api.dto.ResolvedChannelBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelIdentityServiceTest {
    private final ExternalIdentityBindingMapper bindingMapper = mock(ExternalIdentityBindingMapper.class);
    private final AuthProperties properties = properties();
    private final ChannelIdentityService service = new ChannelIdentityService(
            mock(ChannelBindingChallengeMapper.class), bindingMapper, mock(StaffMapper.class),
            mock(AuthTokenService.class), mock(TokenUtil.class), properties);

    @Test
    void batchResolveDeduplicatesIdsAndReturnsOnlyMapperResults() {
        BatchResolveBindingRequest request = request("FEISHU", List.of(1L, 1L, 2L));
        ResolvedChannelBinding binding = new ResolvedChannelBinding(1L, "tenant", "open");
        when(bindingMapper.resolveActiveBindings("FEISHU", List.of(1L, 2L))).thenReturn(List.of(binding));

        assertThat(service.batchResolve("service-key", request).getBindings()).containsExactly(binding);
        verify(bindingMapper).resolveActiveBindings("FEISHU", List.of(1L, 2L));
    }

    @Test
    void batchResolveRejectsInvalidServiceKey() {
        assertThatThrownBy(() -> service.batchResolve("wrong", request("FEISHU", List.of(1L))))
                .isInstanceOf(SecurityException.class);
    }

    private BatchResolveBindingRequest request(String platform, List<Long> ids) {
        BatchResolveBindingRequest request = new BatchResolveBindingRequest();
        request.setPlatform(platform);
        request.setUserIds(ids);
        return request;
    }

    private static AuthProperties properties() {
        AuthProperties properties = new AuthProperties();
        properties.setChannelServiceKey("service-key");
        return properties;
    }
}
