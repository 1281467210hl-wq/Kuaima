-- 老板端成员编辑页新增「备注」字段（与 EnterpriseMember.remark 对应；JPA ddl-auto=update 可自动建列，此脚本供线上手工留档）
ALTER TABLE enterprise_member ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注';
