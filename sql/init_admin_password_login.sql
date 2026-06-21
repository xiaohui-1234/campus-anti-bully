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
