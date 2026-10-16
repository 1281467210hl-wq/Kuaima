CREATE TABLE IF NOT EXISTS admin_agreement (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(60) NOT NULL,
  content_mode VARCHAR(10) NOT NULL,
  content TEXT,
  file_object_key VARCHAR(500),
  file_name VARCHAR(200),
  file_size BIGINT,
  enabled BIT(1) NOT NULL,
  upload_admin_id BIGINT,
  upload_account VARCHAR(100),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_admin_agreement_type (type),
  KEY idx_admin_agreement_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
