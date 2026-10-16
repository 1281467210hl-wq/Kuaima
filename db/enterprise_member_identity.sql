-- 1016 原型调整：企业成员新增 性别/身份证号/年龄 三个字段
-- 对应 EnterpriseMember.gender / idCard / age；ddl-auto=update 会自动建列，本文件供生产手工执行/留档
-- 年龄优先按身份证号实时计算，仅当身份证为空时回退到本列存储值（管理员可手工覆盖）
ALTER TABLE enterprise_member
    ADD COLUMN gender   VARCHAR(10) NULL COMMENT '性别:male男/female女',
    ADD COLUMN id_card  VARCHAR(32) NULL COMMENT '身份证号',
    ADD COLUMN age      INT NULL COMMENT '年龄（可由身份证计算，此为存储/覆盖值）';
