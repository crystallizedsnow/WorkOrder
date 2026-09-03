package com.example.spring_vue_demo.service.helper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.spring_vue_demo.entity.HandleUserInfo;
import com.example.spring_vue_demo.mapper.HandleUserInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderHelperTest {
    @Mock private HandleUserInfoMapper handleUserInfoMapper;
    @InjectMocks private WorkOrderHelper helper;

    @Test
    void unfinishedHandlersAreMappedByUserIdAndDeduplicated() {
        when(handleUserInfoMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                handler(9L, 21L), handler(9L, 21L), handler(9L, 22L)));

        assertThat(helper.getUnfinishedHandlerUserIds(9L)).containsExactly(21L, 22L);

        verify(handleUserInfoMapper).selectList(org.mockito.ArgumentMatchers.any(LambdaQueryWrapper.class));
    }

    private HandleUserInfo handler(Long orderId, Long userId) {
        HandleUserInfo info = new HandleUserInfo();
        info.setOrderId(orderId);
        info.setUserId(userId);
        return info;
    }
}
