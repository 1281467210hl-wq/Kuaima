-- 老板端候选人/面试安排：position_application 新增面试地点与面试官列
-- 对应实体 com.kuaima.app.domain.recruit.entity.PositionApplication（JPA ddl-auto=update 会自动建列，此文件为手工留档/生产变更脚本）
-- 执行：mysql -u<user> -p<password> <db> < boss_application_interview_schema.sql

ALTER TABLE position_application
    ADD COLUMN interview_location VARCHAR(200) NULL COMMENT '面试地点（boss 端面试安排录入）',
    ADD COLUMN interviewer VARCHAR(100) NULL COMMENT '面试官（boss 端面试安排录入）';

-- 状态机扩为 6 值（status 列已存在，仅枚举含义扩展，无需 DDL）：
-- PENDING 待筛选 / PREINTERVIEW 待面试 / INTERVIEWING 面试中 / HIRED 已录用 / REJECTED 已拒绝
