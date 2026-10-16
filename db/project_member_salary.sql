-- 1016 原型调整：项目成员新增「日薪」列（取值来源为岗位薪资范围 salaryRange）
-- 对应 ProjectMember.salary；添加成员时按匹配岗位写入，接口直接回显
ALTER TABLE project_member
    ADD COLUMN salary VARCHAR(50) NULL COMMENT '日薪，来源岗位薪资范围，如 200元/天';
