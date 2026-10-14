-- =====================================================================
-- 快马日结 - admin-web「企业成员」页后端补齐 DDL（MySQL 8.0+）
-- 对应接口: /admin/enterprises/{enterpriseId}/members/**（AdminEnterpriseMemberController）
--           /admin/enterprises/{enterpriseId}/workbench-perms（AdminWorkbenchPermController）
-- 说明: JPA ddl-auto=update 会自动建表/补列，本脚本用于手工环境留档与校验。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. enterprise_member 补列：绑定后台角色 employee_role.id
-- ---------------------------------------------------------------------
ALTER TABLE `enterprise_member`
    ADD COLUMN `role_id` BIGINT NULL COMMENT '绑定角色 employee_role.id，NULL=仅按 member_role 粗粒度角色';

-- ---------------------------------------------------------------------
-- 2. 新表: enterprise_member_project（企业成员-项目参与关系）
--    project 表已由项目管理模块补 enterprise_id 列，见 project 相关脚本
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_member_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `date` DATE COMMENT '创建日期',
    `create_by` BIGINT COMMENT '创建人ID',
    `timestamp` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `member_id` BIGINT NOT NULL COMMENT '关联 enterprise_member.id',
    `project_id` BIGINT NOT NULL COMMENT '关联 project.id',
    `role_in_project` VARCHAR(50) DEFAULT NULL COMMENT '项目中角色，如 项目负责人/人事主管',
    `joined_date` DATE DEFAULT NULL COMMENT '加入项目日期',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE参与中/LEFT已退出',
    PRIMARY KEY (`id`),
    KEY `idx_emp_member` (`member_id`),
    KEY `idx_emp_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业成员-项目参与关系';

-- ---------------------------------------------------------------------
-- 3. 权限码统一（无 DDL 变更）：
--    enterprise_member.permissions JSON 数组统一存大写 code（见 WorkbenchPermCodes）：
--    核心 6 项 POSITION_PUBLISH/MEMBER_MANAGE/PAYROLL_CREATE/PAYROLL_APPROVE/
--    ATTENDANCE_STATS/EXPORT_RECORDS（顺序即 EnterpriseMemberService.permIndexes 0..5，
--    对应旧中文「岗位发布与编辑/成员管理与邀请/制单发薪/发薪审批/考勤统计/记录导出」，
--    读取时经别名表归一化，存量数据无需迁移）；
--    复用既有 HOME_VIEW/ORDER_VIEW/MEMBER_VIEW/MEMBER_WRITE 等 *_VIEW/*_WRITE 码。
-- 存量清洗（可选，将旧中文名权限改写为 code）：
-- UPDATE enterprise_member
--   SET permissions = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(permissions,
--       '岗位发布与编辑','POSITION_PUBLISH'),'成员管理与邀请','MEMBER_MANAGE'),
--       '制单发薪','PAYROLL_CREATE'),'发薪审批','PAYROLL_APPROVE'),
--       '考勤统计','ATTENDANCE_STATS'),'记录导出','EXPORT_RECORDS')
-- WHERE permissions LIKE '%[%';
-- ---------------------------------------------------------------------
