package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 *
 * @author WangDayu
 * @date 2025/6/7
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("department")
public class Department {
    @TableId(type = IdType.AUTO)
    @Schema(description = "部门id")
    private Integer id;
    @Schema(description = "部门名")
    private String name;
    @Schema(description = "部门编码")
    private String code;
    @Schema(description = "父部门编码")
    private String parentDepartmentCode;
    @Schema(description = "所属公司编码")
    private String companyCode;
    @Schema(description = "部门主管编码")
    private String leaderNumber;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
