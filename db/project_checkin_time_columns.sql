-- 项目规定签到/签退时间列（ddl-auto=update 自动建列，本文件供生产手工执行/留档）
-- 2026-10-13

ALTER TABLE `project`
    ADD COLUMN `checkin_time` VARCHAR(5) NULL COMMENT '规定签到时间，HH:mm，如 08:00',
    ADD COLUMN `checkout_time` VARCHAR(5) NULL COMMENT '规定签退时间，HH:mm，如 18:00';
