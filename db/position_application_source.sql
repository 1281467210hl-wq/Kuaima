-- 1016 原型调整：候选人支持从「简历库-加入候选人」产生，标记来源
-- 对应 PositionApplication.source；APPLY=应聘自动进入，MANUAL=简历库手工加入
-- 现有数据默认按 APPLY 处理；加入候选人时若已存在同一(position,resume)则不重复插入
ALTER TABLE position_application
    ADD COLUMN source VARCHAR(20) NULL DEFAULT 'APPLY' COMMENT '候选人来源:APPLY应聘/MANUAL简历库加入';
