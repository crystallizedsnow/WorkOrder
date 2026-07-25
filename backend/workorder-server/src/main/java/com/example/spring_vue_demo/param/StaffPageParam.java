package com.example.spring_vue_demo.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StaffPageParam extends BasePageParam {
    @Schema(description = "主键id")
    private Long Id;

    @Schema(description = "员工工号")
    private String staffNumber;

    @Schema(description = "员工姓名")
    private String name;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "所属公司代码")
    private String companyCode;

    @Schema(description = "公司")
    private String company;

    @Schema(description = "部门编码")
    private String departmentCode;

    @Schema(description = "部门")
    private String department;

    @Schema(description = "职位")
    private String position;

    @Schema(description = "状态（0：正常，1：休假，2：停职 3：离职")
    private Integer status;

    @Schema(description = "直属领导编码")
    private String managerNumber;

    @Schema(description = "直属领导")
    private String managerName;

    @Schema(description = "电话号码")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "创建时间起")
    private Long createTimeFrom;

    @Schema(description = "创建时间止")
    private Long createTimeTo;

}
