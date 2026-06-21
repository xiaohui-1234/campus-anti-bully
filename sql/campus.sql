CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus;

CREATE TABLE IF NOT EXISTS device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'auto increment primary key',
    device_id VARCHAR(64) NOT NULL COMMENT 'public device id',
    product_type VARCHAR(64) NOT NULL COMMENT 'product type',
    device_secret VARCHAR(255) NOT NULL COMMENT 'device secret hash, never store plaintext',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'device register time',
    last_online_time DATETIME DEFAULT NULL COMMENT 'last online time',
    UNIQUE KEY uk_device_id (device_id),
    KEY idx_product_type (product_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='device';

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'auto increment primary key',
    user_id VARCHAR(64) NOT NULL COMMENT 'public user id',
    openid_hash VARCHAR(255) DEFAULT NULL COMMENT 'wechat openid hash, never store plaintext openid',
    security_email_hash CHAR(64) DEFAULT NULL COMMENT 'security email HMAC hash',
    security_email_cipher VARCHAR(512) DEFAULT NULL COMMENT 'encrypted security email',
    security_email_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether security email is verified',
    security_email_verified_time DATETIME DEFAULT NULL COMMENT 'security email verified time',
    security_email_update_time DATETIME DEFAULT NULL COMMENT 'security email last update time',
    password_hash VARCHAR(255) DEFAULT NULL COMMENT 'password hash',
    password_set_time DATETIME DEFAULT NULL COMMENT 'password set time',
    token_version INT NOT NULL DEFAULT 0 COMMENT 'refresh token version',
    nickname VARCHAR(64) DEFAULT NULL COMMENT 'nickname',
    email VARCHAR(128) DEFAULT NULL COMMENT 'notification email, not used for login',
    phone VARCHAR(32) DEFAULT NULL COMMENT 'phone',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT 'avatar url',
    role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME DEFAULT NULL COMMENT 'update time',
    UNIQUE KEY uk_user_id (user_id),
    UNIQUE KEY uk_openid_hash (openid_hash),
    UNIQUE KEY uk_security_email_hash (security_email_hash),
    KEY idx_phone (phone),
    KEY idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user';

CREATE TABLE IF NOT EXISTS user_device_bind (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'auto increment primary key',
    device_table_id BIGINT NOT NULL COMMENT 'foreign key to device.id',
    user_table_id BIGINT NOT NULL COMMENT 'foreign key to user.id',
    bind_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'bind time',
    device_name VARCHAR(64) DEFAULT NULL COMMENT 'custom device name',
    location VARCHAR(128) DEFAULT NULL COMMENT 'custom location',
    note VARCHAR(255) DEFAULT NULL COMMENT 'note',
    UNIQUE KEY uk_user_device (user_table_id, device_table_id),
    KEY idx_user_table_id (user_table_id),
    KEY idx_device_table_id (device_table_id),
    CONSTRAINT fk_bind_device FOREIGN KEY (device_table_id) REFERENCES device(id),
    CONSTRAINT fk_bind_user FOREIGN KEY (user_table_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user device binding';

CREATE TABLE IF NOT EXISTS event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'auto increment primary key',
    mqtt_msg_id VARCHAR(128) NOT NULL COMMENT 'MQTT message id for dedup',
    device_table_id BIGINT NOT NULL COMMENT 'foreign key to device.id',
    event_id VARCHAR(64) NOT NULL COMMENT 'public event id',
    event_type VARCHAR(64) NOT NULL COMMENT 'event type from device',
    alarm_info VARCHAR(255) DEFAULT NULL COMMENT 'alarm info',
    file_key VARCHAR(512) DEFAULT NULL COMMENT 'object storage key',
    file_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING/SUCCESS/FAILED',
    push_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUSHED/FAILED',
    read_status VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT 'UNREAD/READ',
    event_time DATETIME NOT NULL COMMENT 'device event time',
    UNIQUE KEY uk_event_id (event_id),
    UNIQUE KEY uk_device_mqtt_msg (device_table_id, mqtt_msg_id),
    KEY idx_device_event_time (device_table_id, event_time),
    KEY idx_push_status (push_status),
    KEY idx_read_status (read_status),
    CONSTRAINT fk_event_device FOREIGN KEY (device_table_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='event';

CREATE TABLE IF NOT EXISTS device_config_wifi (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'auto increment primary key',
    device_table_id BIGINT NOT NULL COMMENT 'foreign key to device.id',
    config_id INT NOT NULL COMMENT 'device internal config id',
    wifi_name VARCHAR(128) NOT NULL COMMENT 'WiFi name',
    set_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'last update time',
    UNIQUE KEY uk_device_config (device_table_id, config_id),
    KEY idx_device_table_id (device_table_id),
    CONSTRAINT fk_wifi_device FOREIGN KEY (device_table_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='device WiFi config';

INSERT INTO device (device_id, product_type, device_secret)
VALUES ('dev001', 'anti_bullying', SHA2('dev-secret', 256))
ON DUPLICATE KEY UPDATE product_type = VALUES(product_type);

INSERT INTO `user` (
    user_id,
    openid_hash,
    security_email_hash,
    security_email_cipher,
    security_email_verified,
    security_email_verified_time,
    security_email_update_time,
    password_hash,
    password_set_time,
    token_version,
    nickname,
    email,
    role,
    update_time
)
VALUES (
    'usr_admin',
    SHA2('dev_openid_admin', 256),
    '9574744a46ef59f6dc4481fe36b796b2a6fe972dc250d906e90de15d24a3c18a',
    'dqeX9dbuNAbERatSYbYitqcCG6yOjB58V4F7PowCCCmmLl1XhKuKY284/dl5',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '$2a$10$jCspHfrUVWm0AddCeUKjwO80SjjzpeUY0AuoAdQahZ7lZEwGhjEbq',
    CURRENT_TIMESTAMP,
    0,
    'system admin',
    'admin@example.com',
    'ADMIN',
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    role = 'ADMIN',
    security_email_hash = IFNULL(security_email_hash, VALUES(security_email_hash)),
    security_email_cipher = IFNULL(security_email_cipher, VALUES(security_email_cipher)),
    security_email_verified = IF(security_email_verified = 0, VALUES(security_email_verified), security_email_verified),
    security_email_verified_time = IFNULL(security_email_verified_time, VALUES(security_email_verified_time)),
    security_email_update_time = IFNULL(security_email_update_time, VALUES(security_email_update_time)),
    password_hash = IFNULL(password_hash, VALUES(password_hash)),
    password_set_time = IFNULL(password_set_time, VALUES(password_set_time)),
    token_version = IFNULL(token_version, 0),
    email = IFNULL(email, VALUES(email)),
    update_time = CURRENT_TIMESTAMP;
