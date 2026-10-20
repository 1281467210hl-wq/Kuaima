-- =============================================================
-- 人才库模块（老板端 uniapp：人才库 + 雇佣零工）
-- 适用：MySQL 8.x / InnoDB / utf8mb4
-- 说明：表结构可由 JPA ddl-auto=update 自动建表；本脚本用于
--       初始化、CI 环境与手动补表，可重复执行（IF NOT EXISTS）。
-- =============================================================

CREATE TABLE IF NOT EXISTS `talent_pool` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `boss_id`       BIGINT       NOT NULL COMMENT '老板用户 id',
  `worker_id`     BIGINT       DEFAULT NULL COMMENT '关联零工用户 id',
  `name`          VARCHAR(50)  NOT NULL COMMENT '零工姓名/昵称',
  `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '联系电话（脱敏）',
  `avatar_color`  VARCHAR(60)  DEFAULT '#FF6B35,#FF8C5A' COMMENT '头像渐变色，如 #FF6B35,#FF8C5A',
  `type`          VARCHAR(20)  NOT NULL DEFAULT 'new' COMMENT '类型:skilled熟练工/new新零工',
  `experience`    VARCHAR(50)  DEFAULT NULL COMMENT '经验描述，如 3年经验/首单新人',
  `work_years`    INT          DEFAULT NULL COMMENT '工作年限（年）',
  `category`      VARCHAR(50)  DEFAULT NULL COMMENT '工种类别，如 电商分拣',
  `good_rate`     VARCHAR(10)  DEFAULT NULL COMMENT '好评率，如 98% 或 无',
  `rating`        DECIMAL(3,1) DEFAULT NULL COMMENT '综合评分，如 4.8',
  `completed_orders` INT       NOT NULL DEFAULT 0 COMMENT '完成订单数',
  `arrival_rate`  VARCHAR(10)  DEFAULT NULL COMMENT '到岗率，如 100%',
  `skills`        VARCHAR(200) DEFAULT NULL COMMENT '技能标签，逗号分隔',
  `region`        VARCHAR(100) DEFAULT NULL COMMENT '常驻区域',
  `available_time` VARCHAR(100) DEFAULT NULL COMMENT '可工作时间',
  `favorite`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否收藏',
  `source`        VARCHAR(20)  NOT NULL DEFAULT 'auto' COMMENT '来源:auto自动加入/order订单加入/qr扫码加入/manual手动加入',
  `create_by`     BIGINT       DEFAULT NULL COMMENT '创建人',
  `date`          DATE         DEFAULT NULL COMMENT '创建日期',
  `timestamp`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tp_boss` (`boss_id`),
  KEY `idx_tp_worker` (`worker_id`),
  KEY `idx_tp_type` (`type`),
  KEY `idx_tp_favorite` (`favorite`),
  UNIQUE KEY `uk_talent_pool_boss_worker` (`boss_id`, `worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老板人才库';

CREATE TABLE IF NOT EXISTS `talent_hire` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `boss_id`     BIGINT       NOT NULL COMMENT '老板用户 id',
  `talent_id`   BIGINT       NOT NULL COMMENT '关联 talent_pool.id',
  `worker_id`   BIGINT       DEFAULT NULL COMMENT '关联零工用户 id',
  `job_id`      BIGINT       DEFAULT NULL COMMENT '关联岗位/订单 id（选填）',
  `job_name`    VARCHAR(100) DEFAULT NULL COMMENT '岗位名称快照',
  `work_date`   DATE         DEFAULT NULL COMMENT '工作日期',
  `note`        VARCHAR(500) DEFAULT NULL COMMENT '备注/给零工留言',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态:pending待确认/confirmed已确认/finished已完成/cancelled已取消',
  `create_by`   BIGINT       DEFAULT NULL COMMENT '创建人',
  `date`        DATE         DEFAULT NULL COMMENT '创建日期',
  `timestamp`   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_th_boss` (`boss_id`),
  KEY `idx_th_talent` (`talent_id`),
  KEY `idx_th_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人才库雇佣记录';

CREATE TABLE IF NOT EXISTS `talent_review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`   BIGINT       NOT NULL COMMENT '关联 talent_pool.id',
  `boss_id`     BIGINT       DEFAULT NULL COMMENT '评价老板用户 id',
  `job_title`   VARCHAR(100) DEFAULT NULL COMMENT '工作岗位标题',
  `location`    VARCHAR(100) DEFAULT NULL COMMENT '工作地点',
  `review_date` DATE         DEFAULT NULL COMMENT '评价日期',
  `stars`       INT          NOT NULL DEFAULT 5 COMMENT '星级 1-5',
  `content`     VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `create_by`   BIGINT       DEFAULT NULL COMMENT '创建人',
  `date`        DATE         DEFAULT NULL COMMENT '创建日期',
  `timestamp`   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tr_talent` (`talent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人才库雇主评价';

-- 初始化示例数据（boss_id=1 演示数据）
INSERT INTO `talent_pool` (`boss_id`, `name`, `phone`, `avatar_color`, `type`, `experience`, `work_years`, `category`, `good_rate`, `rating`, `completed_orders`, `arrival_rate`, `skills`, `region`, `available_time`, `favorite`, `source`, `date`) VALUES
(1, '张师傅', '138****5678', '#FF6B35,#FF8C5A', 'skilled', '3年经验', 3, '电商分拣', '98%', 4.8, 12, '100%', '分拣,打包', '松江区泗泾镇 · 九亭镇', '工作日/周末均可 · 全天', 1, 'auto', CURDATE()),
(1, '李阿姨', '139****1234', '#52C41A,#73D13D', 'skilled', '5年经验', 5, '餐饮服务', '100%', 4.9, 20, '100%', '餐饮,服务员', '浦东新区 · 周浦镇', '工作日 · 全天', 0, 'auto', CURDATE()),
(1, '王师傅', '137****8899', '#1890FF,#40A9FF', 'skilled', '4年经验', 4, '快递搬运', '95%', 4.6, 15, '95%', '搬运,装卸', '青浦区 · 赵巷镇', '周末 · 全天', 1, 'order', CURDATE()),
(1, '陈小弟', '136****5566', '#FA8C16,#FFC53D', 'new', '首单新人', 0, '快递分拣', '90%', 4.5, 1, '100%', '分拣,扫描', '松江区 · 方松街道', '周末 · 上午', 0, 'qr', CURDATE()),
(1, '赵同学', '135****2233', '#EB2F96,#F759AB', 'new', '首单新人', 0, '餐饮协助', '无', NULL, 0, NULL, '传菜,帮工', '闵行区 · 莘庄镇', '周末 · 全天', 0, 'qr', CURDATE()),
(1, '刘大哥', '138****7788', '#722ED1,#9254DE', 'new', '1单完成', 1, '仓库理货', '100%', 5.0, 1, '100%', '理货,盘点', '嘉定区 · 安亭镇', '工作日 · 全天', 0, 'auto', CURDATE())
ON DUPLICATE KEY UPDATE `timestamp` = CURRENT_TIMESTAMP;

INSERT INTO `talent_review` (`talent_id`, `boss_id`, `job_title`, `location`, `review_date`, `stars`, `content`, `date`)
SELECT p.`id`, 1, '电商分拣打包工', '松江 · 泗泾', '2026-09-08', 5, '干活麻利，准时到岗，推荐！', CURDATE()
FROM `talent_pool` p WHERE p.`boss_id` = 1 AND p.`name` = '张师傅'
AND NOT EXISTS (SELECT 1 FROM `talent_review` r WHERE r.`talent_id` = p.`id`);

INSERT INTO `talent_review` (`talent_id`, `boss_id`, `job_title`, `location`, `review_date`, `stars`, `content`, `date`)
SELECT p.`id`, 1, '快递装卸工', '青浦 · 赵巷', '2026-08-30', 5, '态度好，效率高。', CURDATE()
FROM `talent_pool` p WHERE p.`boss_id` = 1 AND p.`name` = '张师傅'
AND NOT EXISTS (SELECT 1 FROM `talent_review` r WHERE r.`talent_id` = p.`id` AND r.`job_title` = '快递装卸工');
