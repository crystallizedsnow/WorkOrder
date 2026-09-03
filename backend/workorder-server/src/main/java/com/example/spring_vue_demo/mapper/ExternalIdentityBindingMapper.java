package com.example.spring_vue_demo.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.spring_vue_demo.entity.ExternalIdentityBinding;
import com.example.workorder.api.dto.ResolvedChannelBinding;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ExternalIdentityBindingMapper extends BaseMapper<ExternalIdentityBinding> {
    @Select("<script>" +
            "SELECT DISTINCT b.user_id AS userId, b.tenant_key AS tenantKey, b.open_id AS openId " +
            "FROM external_identity_binding b INNER JOIN staff s ON s.id = b.user_id " +
            "WHERE b.platform = #{platform} AND b.status = 'ACTIVE' " +
            "AND b.open_id IS NOT NULL AND b.open_id &lt;&gt; '' AND s.status = 0 " +
            "AND b.user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<ResolvedChannelBinding> resolveActiveBindings(@Param("platform") String platform,
                                                       @Param("userIds") List<Long> userIds);
}
