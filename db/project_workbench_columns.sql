-- 企业工作台-项目管理 缺列补齐（ddl-auto=update 会自动加列，本文件用于生产手工执行/留档）
-- 2026-10-13

ALTER TABLE `project`
    ADD COLUMN `code` VARCHAR(50) NULL COMMENT '项目编号，如 PRJ-2026-001，为空时接口按 PRJ-{id} 兜底';

ALTER TABLE `project_member`
    ADD COLUMN `employment_type` VARCHAR(20) NULL COMMENT '用工类型:fulltime正式工/temp临时工，为空按 status 推断',
    ADD COLUMN `leave_date` DATE NULL COMMENT '离职日期';

ALTER TABLE `project_onsite_staff`
    ADD COLUMN `position` VARCHAR(50) NULL COMMENT '驻场岗位，如 项目助理/安全督导',
    ADD COLUMN `start_date` DATE NULL COMMENT '驻场开始日期',
    ADD COLUMN `end_date` DATE NULL COMMENT '驻场结束日期',
    ADD COLUMN `onsite_status` VARCHAR(20) NULL DEFAULT 'ongoing' COMMENT '驻场状态:ongoing驻场中/ended已结束';

ALTER TABLE `attendance_record`
    ADD COLUMN `sign_in_location` VARCHAR(200) NULL COMMENT '签到地点',
    ADD COLUMN `sign_out_location` VARCHAR(200) NULL COMMENT '签退地点';
