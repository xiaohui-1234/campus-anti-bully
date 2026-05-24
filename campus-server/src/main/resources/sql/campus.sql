CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus;

CREATE TABLE IF NOT EXISTS device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键自增',
    device_id VARCHAR(64) NOT NULL COMMENT '设备对外唯一标识',
    product_type VARCHAR(64) NOT NULL COMMENT '产品类型，同时可作为 EMQX 用户名/账号分组',
    device_secret VARCHAR(255) NOT NULL COMMENT '设备密钥哈希值，不保存明文',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '设备生产/注册时间',
    last_online_time DATETIME DEFAULT NULL COMMENT '最后一次在线时间',
    UNIQUE KEY uk_device_id (device_id),
    KEY idx_product_type (product_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键自增',
    user_id VARCHAR(64) NOT NULL COMMENT '用户对外唯一标识',
    openid_hash VARCHAR(255) NOT NULL COMMENT '微信 openid 哈希值，不保存明文 openid',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '用户名/昵称',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像访问URL',
    role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '权限：USER/ADMIN',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    UNIQUE KEY uk_user_id (user_id),
    UNIQUE KEY uk_openid_hash (openid_hash),
    KEY idx_phone (phone),
    KEY idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS user_device_bind (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键id',
    device_table_id BIGINT NOT NULL COMMENT '外键，对应 device.id',
    user_table_id BIGINT NOT NULL COMMENT '外键，对应 user.id',
    bind_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    device_name VARCHAR(64) DEFAULT NULL COMMENT '用户给绑定设备设置的名称',
    location VARCHAR(128) DEFAULT NULL COMMENT '设备所在地点备注',
    note VARCHAR(255) DEFAULT NULL COMMENT '设备备注信息',
    UNIQUE KEY uk_user_device (user_table_id, device_table_id),
    KEY idx_user_table_id (user_table_id),
    KEY idx_device_table_id (device_table_id),
    CONSTRAINT fk_bind_device FOREIGN KEY (device_table_id) REFERENCES device(id),
    CONSTRAINT fk_bind_user FOREIGN KEY (user_table_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备绑定表';

CREATE TABLE IF NOT EXISTS event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键id',
    mqtt_msg_id VARCHAR(128) NOT NULL COMMENT 'MQTT消息id，用于数据库兜底去重',
    device_table_id BIGINT NOT NULL COMMENT '外键，事件所属设备，对应 device.id',
    event_id VARCHAR(64) NOT NULL COMMENT '对外事件ID',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型，由设备决定并上报',
    alarm_info VARCHAR(255) DEFAULT NULL COMMENT '报警信息',
    file_key VARCHAR(512) DEFAULT NULL COMMENT '语音/图片/视频文件对象存储key',
    file_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADING' COMMENT '文件上传状态：UPLOADING/SUCCESS/FAILED',
    push_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '推送状态：PENDING/PUSHED/FAILED',
    read_status VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD/READ',
    event_time DATETIME NOT NULL COMMENT '设备事件发生时间',
    UNIQUE KEY uk_event_id (event_id),
    UNIQUE KEY uk_device_mqtt_msg (device_table_id, mqtt_msg_id),
    KEY idx_device_event_time (device_table_id, event_time),
    KEY idx_push_status (push_status),
    KEY idx_read_status (read_status),
    CONSTRAINT fk_event_device FOREIGN KEY (device_table_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件表';

CREATE TABLE IF NOT EXISTS device_config_wifi (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键id',
    device_table_id BIGINT NOT NULL COMMENT '外键，指向 device.id',
    config_id INT NOT NULL COMMENT '设备内部某一配置编号',
    wifi_name VARCHAR(128) NOT NULL COMMENT 'WiFi名称',
    set_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上一次更新时间',
    UNIQUE KEY uk_device_config (device_table_id, config_id),
    KEY idx_device_table_id (device_table_id),
    CONSTRAINT fk_wifi_device FOREIGN KEY (device_table_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备WiFi配置表';
