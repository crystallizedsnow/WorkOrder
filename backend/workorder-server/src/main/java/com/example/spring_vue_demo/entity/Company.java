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
@TableName("company")
@AllArgsConstructor
@NoArgsConstructor
public class Company {
    @TableId(type = IdType.AUTO)
    @Schema(description = "公司id")
    private Integer id;
    @Schema(description="公司代码")
    private String code;
    @Schema(description="公司名")
    private String name;
    @Schema(description="父公司代码")
    private String parentCompanyCode;
    @Schema(description="公司等级")
    private int level;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
