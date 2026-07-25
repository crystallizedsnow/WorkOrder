package com.example.spring_vue_demo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StaffBelongInfoVO {
    @Schema(description = "员工id")
    private String userId;
    @Schema(description = "员工姓名")
    private String name;
    @Schema(description = "员工编码")
    private String staffNumber;
    @Schema(description = "公司编码")
    private String companyCode;
    @Schema(description = "公司名")
    private String companyName;
    @Schema(description = "部门编码")
    private String departmentCode;
    @Schema(description = "部门名")
    private String departmentName;
    @Schema(description = "职位")
    private String position;
    @Schema(description = "直属领导编码")
    private String managerNumber;
    @Schema(description = "直属领导")
    private String managerName;
    @Schema(description = "手机号码")
    private String phone;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "角色：admin/user")
    private String role;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
