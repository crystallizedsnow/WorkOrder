package com.example.spring_vue_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.spring_vue_demo.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Select("""
            SELECT id
              FROM work_order
             WHERE status IN (400, 670)
               AND deadline_time <= #{scanTime}
               AND deleted = 0
             ORDER BY deadline_time, id
             LIMIT #{limit}
            """)
    List<WorkOrder> selectOverdueCandidates(@Param("scanTime") LocalDateTime scanTime,
                                            @Param("limit") int limit);

    @Update("""
            UPDATE work_order
               SET status = 410
             WHERE id = #{orderId}
               AND status IN (400, 670)
               AND deadline_time <= #{scanTime}
               AND deleted = 0
            """)
    int markOverdue(@Param("orderId") Long orderId, @Param("scanTime") LocalDateTime scanTime);
}
