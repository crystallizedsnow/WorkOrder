package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.spring_vue_demo.entity.Company;
import com.example.spring_vue_demo.entity.Department;
import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.mapper.CompanyMapper;
import com.example.spring_vue_demo.mapper.DepartmentMapper;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.param.AddCompanyParam;
import com.example.spring_vue_demo.service.CompanyService;
import com.example.spring_vue_demo.utils.OrderCodeUtils;
import com.example.spring_vue_demo.utils.OrganizationCodeUtils;
import com.example.spring_vue_demo.vo.AddCompanyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.annotation.Retention;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author WangDayu
 * @date 2025/6/7
 */

@Service
public class CompanyServiceImpl extends ServiceImpl<CompanyMapper, Company> implements CompanyService {
    @Autowired
    private CompanyMapper companyMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Autowired
    private StaffMapper staffMapper;
    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public Result addCompany(AddCompanyParam param) {
        String code = OrganizationCodeUtils.generateCompanyCode();
        Company company = new Company();
        company.setName(param.getName());
        company.setLevel(param.getLevel());
        log.debug(param.toString());
        //保证公司名不重复
        Company newCompany = companyMapper.selectOne(
                new QueryWrapper<Company>().eq("name",param.getName())
        );
        if(newCompany!=null){
            return Result.error(param.getName()+"已经存在");
        }
        if (param.getParentCompanyName() != null && !param.getParentCompanyName().isEmpty()) {
            Company parent = companyMapper.selectOne(
                    new QueryWrapper<Company>().eq("name", param.getParentCompanyName())
            );
            if (parent == null) {
                return Result.error("未找到父公司名称为：" + param.getParentCompanyName());
            }
            company.setParentCompanyCode(parent.getCode());
        } else {
            company.setParentCompanyCode(null); // 或者不设置，默认数据库为 NULL
        }

        company.setCode(code);
        boolean isSaved = this.save(company);
        if(isSaved){
            AddCompanyVO addCompanyVO = new AddCompanyVO();
            addCompanyVO.setCompanyCode(company.getCode());
            addCompanyVO.setId(company.getId());
            return Result.success(addCompanyVO);
        }
        return Result.error("插入失败");
    }

    @Override
    public Result allCompany() {
        List<Company> companies = companyMapper.selectList(null);
        return Result.success(companies);
    }

    @Override
    public Result delete(Long id) {
        Company company = companyMapper.selectById(id);
        if(company==null){
            return Result.error("没有对应id的公司");
        }
        LambdaQueryWrapper<Staff> staffWrapper = new LambdaQueryWrapper<>();
        staffWrapper.eq(Staff::getCompanyCode, company.getCode());
        staffMapper.delete(staffWrapper);        //删除员工
        //查找该公司的部门
        LambdaQueryWrapper<Department> departmentWrapper = new LambdaQueryWrapper<>();
        departmentWrapper.eq(Department::getCompanyCode, company.getCode());
        List<Department> departments = departmentMapper.selectList(departmentWrapper);
        //更改下级部门的上级为null
        if(!departments.isEmpty()){
            List<String> departmentCodes = departments.stream().
                    map(Department::getCode).collect(Collectors.toList());
            LambdaUpdateWrapper<Department> updateDepartmentWrapper = new LambdaUpdateWrapper<>();
            updateDepartmentWrapper.in(Department::getCode, departmentCodes)
                    .set(Department::getParentDepartmentCode,null);
            departmentMapper.update(updateDepartmentWrapper);
            departmentMapper.update(updateDepartmentWrapper);
        }
        //删除该公司的部门
        departmentMapper.delete(departmentWrapper);
        companyMapper.deleteById(id);
        return Result.success();
    }
}
