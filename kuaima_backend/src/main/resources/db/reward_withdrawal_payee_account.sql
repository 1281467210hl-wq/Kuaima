SET @reward_withdrawal_payee_account_column := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'reward_withdrawal'
          AND COLUMN_NAME = 'payee_account'
    ),
    'ALTER TABLE reward_withdrawal ADD COLUMN payee_account VARCHAR(128) NULL',
    'SELECT 1'
);

PREPARE reward_withdrawal_payee_account_statement
    FROM @reward_withdrawal_payee_account_column;
EXECUTE reward_withdrawal_payee_account_statement;
DEALLOCATE PREPARE reward_withdrawal_payee_account_statement;
