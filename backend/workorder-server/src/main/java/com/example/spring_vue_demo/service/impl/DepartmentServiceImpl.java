package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.spring_vue_demo.entity.Company;
import com.example.spring_vue_demo.entity.Department;
import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.mapper.CompanyMapper;
import com.example.spring_vue_demo.mapper.DepartmentMapper;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.param.AddDepartmentParam;
import com.example.spring_vue_demo.param.ChangeDepartmentParam;
import com.example.spring_vue_demo.service.DepartmentService;
import com.example.spring_vue_demo.utils.OrganizationCodeUtils;
import com.example.spring_vue_demo.vo.AddDepartmentVO;
import com.example.spring_vue_demo.vo.ChangeDepartmentVO;
import lombok.val;
import org.apache.ibatis.type.LongTypeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {
    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private CompanyMapper companyMapper;
    @Autowired
    private StaffMapper staffMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Result addDepartment(AddDepartmentParam param) {
        Department department = new Department();

        //查公司
        Company company = companyMapper.selectOne(
                new QueryWrapper<Company>().eq("name", param.getCompanyName())
        );
        if (company == null) {
            return Result.error("公司不存在: " + param.getCompanyName());
        }
        department.setCompanyCode(company.getCode());
        //确保部门名不重复
        Department nowDepartment = departmentMapper.selectOne(
                new QueryWrapper<Department>().eq("company_code", company.getCode())
                        .eq("name",param.getName())
        );
        if (nowDepartment != null) {
            return Result.error("公司"+company.getName()+"已经存在部门"+param.getName());
        }
        //查上级部门
        if (param.getParentDepartmentName() != null && !param.getParentDepartmentName().isEmpty()) {
            Department parent = departmentMapper.selectOne(
                    new QueryWrapper<Department>()
                            .eq("name", param.getParentDepartmentName())
                            .eq("company_code", company.getParentCompanyCode())
            );
            if (parent == null) {
                return Result.error("父部门不存在: " + param.getParentDepartmentName());
            }
            String parentDepartmentCode = parent.getCode();
            department.setParentDepartmentCode(parentDepartmentCode);
        }
        // 查主管
        Staff leader = staffMapper.selectOne(
                new QueryWrapper<Staff>().eq("name", param.getLeaderName())
        );
        if (leader != null) {
            department.setLeaderNumber(leader.getStaffNumber());
        }
        // 4. 生成部门编码（可根据公司+已有部门数）
        Long departmentCount = departmentMapper.selectCount(
                new QueryWrapper<Department>()
                        .eq("company_code", company.getCode()) // 或者根据实际字段：companyCode、companyName等
        );
        String departmentCode = OrganizationCodeUtils.generateDepartmentCode(company.getCode(), departmentCount);
        department.setCode(departmentCode);
        department.setName(param.getName());
        departmentMapper.insert(department);

        // 6. 返回结果
        AddDepartmentVO vo = new AddDepartmentVO();
        vo.setDepartmentCode(departmentCode);
        vo.setId(department.getId());
        if (leader != null) {
            vo.setLeaderNumber(leader.getStaffNumber());
        }
        return Result.success(vo);
    }

    @Override
    public Result allDepartmentInCompany(String companyName) {
        // 1. 根据公司名查询公司
        Company company = companyMapper.selectOne(
                new QueryWrapper<Company>().eq("name", companyName)
        );

        if (company == null) {
            return Result.error("公司不存在: " + companyName);
        }

        // 2. 查询该公司下的所有部门
        List<Department> departments = departmentMapper.selectList(
                new QueryWrapper<Department>().eq("company_code", company.getCode())
        );
        return Result.success(departments);
    }

    @Transactional
    @Override
    public Result changeDepartment(ChangeDepartmentParam param) {
        if (param.getId() == null) {
            return Result.error("部门id不能为空");
        }

        UpdateWrapper<Department> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", param.getId());

        // 只设置非空字段进行更新
        if (param.getParentDepartmentCode() != null) {
            updateWrapper.set("parent_department_code", param.getParentDepartmentCode());
        }
        if (param.getLeaderNumber() != null) {
            // 查询 leader 的 name（从 staff 表）
            Staff leader = staffMapper.selectOne(
                    new QueryWrapper<Staff>().eq("staff_number", param.getLeaderNumber())
            );
            if (leader == null) {
                return Result.error("找不到编号: " + param.getLeaderNumber() + "的职工");
            }
            updateWrapper.set("leader_number", param.getLeaderNumber());

            // 构造更新操作
            UpdateWrapper<Staff> staffUpdate = new UpdateWrapper<>();
            Department department = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("id", param.getId())
            );
            staffUpdate.eq("department_code", department.getCode()); // 该部门下所有员工
            staffUpdate.set("manager_number", leader.getStaffNumber());
            staffUpdate.set("manager_name", leader.getName());
            staffUpdate.set("update_time", System.currentTimeMillis() / 1000);

            staffMapper.update(null, staffUpdate);
        }
        long updateTime = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        updateWrapper.set("update_time", updateTime / 1000);

        boolean updated = departmentMapper.update(null, updateWrapper) > 0;
        ChangeDepartmentVO changeDepartmentVO = new ChangeDepartmentVO();
        changeDepartmentVO.setId(param.getId());
        changeDepartmentVO.setCode(param.getCode());
        changeDepartmentVO.setParentDepartmentCode(param.getParentDepartmentCode());
        changeDepartmentVO.setLeaderNumber(param.getLeaderNumber());
        changeDepartmentVO.setIsSuccess(updated);
        return Result.success(changeDepartmentVO);

    }

    @Override
    public Result delete(Long id) {
        Department department = departmentMapper.selectById(id);
        if(department == null)
        {
            return Result.error("该部门不存在");
        }
        //查找该部门的员工
        LambdaQueryWrapper<Staff> staffLambdaQueryWrapper = new LambdaQueryWrapper<>();
        staffLambdaQueryWrapper.eq(Staff::getCompanyCode, department.getCompanyCode())
                .eq(Staff::getDepartmentCode,department.getCode());
        staffMapper.delete(staffLambdaQueryWrapper);        //删除员工
        departmentMapper.deleteById(id);
        return Result.success();
    }
}

