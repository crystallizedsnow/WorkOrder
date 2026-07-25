package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.example.spring_vue_demo.param.AddStaffParam;
import com.example.spring_vue_demo.param.ChangeStaffInfoParam;
import com.example.spring_vue_demo.param.StaffPageParam;
import com.example.spring_vue_demo.service.DepartmentService;
import com.example.spring_vue_demo.service.StaffService;
import com.example.spring_vue_demo.utils.OrganizationCodeUtils;
import com.example.spring_vue_demo.vo.AddStaffVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StaffServiceImpl extends ServiceImpl<StaffMapper, Staff> implements StaffService {

    @Autowired
    private StaffMapper staffMapper;
    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private CompanyMapper companyMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Override
    public Result addStaff(AddStaffParam param) {
        Staff staff = new Staff();
        // 1. 查公司信息
        Company company = companyMapper.selectOne(
                new QueryWrapper<Company>().eq("name", param.getCompanyName())
        );
        if (company == null) {
            return Result.error("公司不存在");
        }
        staff.setCompany(param.getCompanyName());
        staff.setCompanyCode(company.getCode());

        // 2. 查部门信息
        Department department = departmentMapper.selectOne(
                new QueryWrapper<Department>()
                        .eq("name", param.getDepartmentName())
                        .eq("company_code", company.getCode())
        );
        if (department == null) {
            return Result.error("部门不存在");
        }
        staff.setDepartment(param.getDepartmentName());
        staff.setDepartmentCode(department.getCode());

        // 3.查主管
        if(param.getManagerName() != null && !param.getManagerName().equals("")) {
            Staff manager = staffMapper.selectOne(
                    new QueryWrapper<Staff>().eq("name", param.getManagerName())
            );
            if (manager == null) {
                return Result.error("主管不存在");
            }
            staff.setManagerName(manager.getManagerName());
            staff.setManagerNumber(manager.getStaffNumber());
        }
        staff.setName(param.getName());
        staff.setStatus(0);
        staff.setPosition(param.getPosition());
        //确保手机号码的唯一性
        // 1. 手机号是否已存在
        Staff phoneExisting = staffMapper.selectOne(
                new QueryWrapper<Staff>().eq("phone", param.getPhone())
        );
        Staff emailExisting = staffMapper.selectOne(
                new QueryWrapper<Staff>().eq("email", param.getEmail())
        );
        if (phoneExisting != null || emailExisting != null) {
            return Result.error("该手机号或者邮箱已经被注册");
        }
        staff.setPhone(param.getPhone());
        staff.setEmail(param.getEmail());
        staff.setPassword("123456");
        String staffNumber = OrganizationCodeUtils.generateStaffCode();
        staff.setStaffNumber(staffNumber);
        staffMapper.insert(staff);

        // 6. 构建返回 VO
        AddStaffVO vo = new AddStaffVO();
        vo.setId(staff.getId());
        vo.setStaffNumber(staffNumber);
        vo.setCompanyCode(staff.getCompanyCode());
        vo.setDepartmentCode(staff.getDepartmentCode());
        vo.setManagerNUmber(staff.getManagerNumber());

        return Result.success(vo);
    }

    @Override
    public Result allStaff() {
        List<Staff> staffList = staffMapper.selectList(null); // 查询所有员工
        return Result.success(staffList);
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
    public Result delete(Long id) {
        int rows = staffMapper.deleteById(id);
        if(rows ==0){
            return Result.error("没有对应id的用户");
        }
        return Result.success();
    }
    @Transactional
    @Override
    public Result changeStaff(ChangeStaffInfoParam param) {
        if (param.getId() == null) {
            return Result.error("员工id不能为空");
        }
        Staff staff = staffMapper.selectById(param.getId());
        UpdateWrapper<Staff> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", param.getId());
        Company company=null;
        Department department=null;
        if (param.getCompanyName() != null) {
            company = companyMapper.selectOne(
                    new QueryWrapper<Company>().eq("name", param.getCompanyName())
            );
            if (company == null) {
                return Result.error("没有名字为"+param.getCompanyName()+"的公司");
            }
            updateWrapper.set("company", param.getCompanyName());
            updateWrapper.set("company_code",company.getCode());
        }
        String companyCode =  (company==null ? staff.getCompanyCode() : company.getCode());
        if (param.getDepartmentName() != null) {
            department = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("name", param.getDepartmentName())
                            .eq("company_code", companyCode)
            );
            if (department == null) {
                return Result.error("不存在名字为"+param.getDepartmentName()+"的部门");
            }
            updateWrapper.set("department", param.getDepartmentName());
            updateWrapper.set("department_code",department.getCode());
        }
        //更新manager-name
        if (company !=null || department != null)
        {
            String departmentCode =  (department==null ? staff.getDepartmentCode() : department.getCode());
            Department dept = departmentMapper.selectOne(
                    new QueryWrapper<Department>().eq("company_code", companyCode)
                    .eq("code", departmentCode)
            );
            Staff leader = staffMapper.selectOne(
                    new QueryWrapper<Staff>().eq("staff_number", dept.getLeaderNumber())
            );
            if(leader != null){
                updateWrapper.set("manager_number", leader.getManagerNumber());
                updateWrapper.set("manager_name",leader.getManagerName());
            }
        }
        if (param.getPosition() != null) {
            updateWrapper.set("position", param.getPosition());
        }

        // 状态可能为0，需判断是否包含
        if(param.getStatus() <0 || param.getStatus() > 3){
            return Result.error("无效状态码");
        }
        updateWrapper.set("status", param.getStatus());

        updateWrapper.set("update_time", System.currentTimeMillis() / 1000);

        staffMapper.update(null, updateWrapper);
        return Result.success();
    }
}
