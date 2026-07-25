package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.spring_vue_demo.entity.Company;
import com.example.spring_vue_demo.entity.Department;
import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.mapper.CompanyMapper;
import com.example.spring_vue_demo.mapper.DepartmentMapper;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.param.ChangePersonalInfoParam;
import com.example.spring_vue_demo.param.StaffPageParam;
import com.example.spring_vue_demo.service.UserService;
import com.example.spring_vue_demo.utils.StaffHolder;
import com.example.spring_vue_demo.vo.OrganizationStructureVO;
import com.example.spring_vue_demo.vo.StaffBelongInfoVO;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<StaffMapper, Staff> implements UserService {

    private final CompanyMapper companyMapper;
    private final DepartmentMapper departmentMapper;
    @Autowired
    private StaffMapper staffMapper;
    public UserServiceImpl(CompanyMapper companyMapper, DepartmentMapper departmentMapper) {
        this.companyMapper = companyMapper;
        this.departmentMapper = departmentMapper;
    }

    @Override
    public Result belong() {
        // 1. 根据ID查Staff
        Long staffId = StaffHolder.get().getId();
        Staff staff = this.getById(staffId);
        if (staff == null) {
            return Result.error("员工不存在");
        }

        // 2. 构造VO
        StaffBelongInfoVO vo = new StaffBelongInfoVO();
        vo.setUserId(staff.getId().toString());
        vo.setName(staff.getName());
        vo.setStaffNumber(staff.getStaffNumber());
        vo.setCompanyCode(staff.getCompanyCode());
        vo.setCompanyName(staff.getCompany());
        vo.setDepartmentCode(staff.getDepartmentCode());
        vo.setDepartmentName(staff.getDepartment());
        vo.setPosition(staff.getPosition());
        vo.setRole(staff.getRole());
        vo.setCreateTime(staff.getCreateTime());
        vo.setUpdateTime(staff.getUpdateTime());
        vo.setManagerNumber(staff.getManagerNumber());
        vo.setManagerName(staff.getManagerName());
        vo.setEmail(staff.getEmail());
        vo.setPhone(staff.getPhone());
        // 3. 返回
        return Result.success(vo);
    }

    @Override
    public Result organizationStructure() {
        List<OrganizationStructureVO> voList = new ArrayList<>();
        //找出所有公司信息
        List<Company> companies = companyMapper.selectList(null);
        for(Company company : companies) {
            OrganizationStructureVO vo = new OrganizationStructureVO();
            vo.setCompany(company);
            List<Department> departmentsInCompany = departmentMapper.selectList(
                    new QueryWrapper<Department>()
                            .eq("company_code", company.getCode())
            );
            vo.setDepartments(departmentsInCompany);
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result staffPage(StaffPageParam param) {
        // 创建分页对象
        Page<Staff> page = new Page<>(param.getPageNum(), param.getPageSize());

        // 构建查询条件
        QueryWrapper<Staff> queryWrapper = new QueryWrapper<>();

        // 精确匹配条件
        if (StringUtils.isNotBlank(param.getStaffNumber())) {
            queryWrapper.eq("staff_number", param.getStaffNumber());
        }
        if (StringUtils.isNotBlank(param.getCompanyCode())) {
            queryWrapper.eq("company_code", param.getCompanyCode());
        }
        if (StringUtils.isNotBlank(param.getDepartmentCode())) {
            queryWrapper.eq("department_code", param.getDepartmentCode());
        }
        if (param.getStatus() != null) {
            queryWrapper.eq("status", param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getManagerNumber())) {
            queryWrapper.eq("manager_number", param.getManagerNumber());
        }
        // 模糊查询条件
        if (StringUtils.isNotBlank(param.getPhone())) {
            queryWrapper.like("phone", param.getPhone());
        }
        if (StringUtils.isNotBlank(param.getEmail())) {
            queryWrapper.like("email", param.getEmail());
        }

        if (StringUtils.isNotBlank(param.getName())) {
            queryWrapper.like("name", param.getName());
        }
        if (StringUtils.isNotBlank(param.getCompany())) {
            queryWrapper.like("company", param.getCompany());
        }
        if (StringUtils.isNotBlank(param.getDepartment())) {
            queryWrapper.like("department", param.getDepartment());
        }
        if (StringUtils.isNotBlank(param.getPosition())) {
            queryWrapper.like("position", param.getPosition());
        }
        if (StringUtils.isNotBlank(param.getManagerName())) {
            queryWrapper.like("manager_name", param.getManagerName());
        }

        // 时间范围查询
        if (param.getCreateTimeFrom()!=null) {
            queryWrapper.ge("create_time", param.getCreateTimeFrom());
        }
        if(param.getCreateTimeTo()!=null) {
            queryWrapper.le("create_time", param.getCreateTimeTo());
        }
        // 执行查询
        Page<Staff> staffPage = staffMapper.selectPage(page, queryWrapper);
        return Result.success(staffPage);
    }

    @Override
    public Result change(ChangePersonalInfoParam param) {
        Staff staff = StaffHolder.get();

        UpdateWrapper<Staff> updateWrapper = new UpdateWrapper<>();

        // 设置更新条件
        updateWrapper.eq("id", staff.getId());

        // 设置更新字段
        if (StringUtils.isNotBlank(param.getPassword())) {
            updateWrapper.set("password", param.getPassword());
        }
        if (StringUtils.isNotBlank(param.getPhone())) {
            updateWrapper.set("phone", param.getPhone());
        }
        if (StringUtils.isNotBlank(param.getEmail())) {
            updateWrapper.set("email", param.getEmail());
        }

        updateWrapper.set("update_time", java.time.LocalDateTime.now());

        int rows = staffMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.error("无更新");
        }
        return Result.success();
    }

    @Override
    public Result allStaff() {
        List<Staff> staffList = staffMapper.selectList(null); // 查询所有员工
        return Result.success(staffList);
    }
}
