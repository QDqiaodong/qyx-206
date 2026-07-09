CREATE DATABASE IF NOT EXISTS fire_training DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fire_training;

CREATE TABLE IF NOT EXISTS equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_code VARCHAR(50) UNIQUE NOT NULL COMMENT '器材编号',
    training_purpose VARCHAR(100) NOT NULL COMMENT '训练用途',
    size_spec VARCHAR(100) COMMENT '尺寸规格',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_equipment_code (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟训练器材表';

CREATE TABLE IF NOT EXISTS team (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_name VARCHAR(50) UNIQUE NOT NULL COMMENT '班组名称',
    member_count INT DEFAULT 0 COMMENT '成员数量',
    description VARCHAR(200) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_team_name (team_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练班组表';

CREATE TABLE IF NOT EXISTS assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL COMMENT '器材ID',
    team_id BIGINT NOT NULL COMMENT '班组ID',
    bind_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    operator VARCHAR(50) COMMENT '操作人',
    remark VARCHAR(200) COMMENT '备注',
    CONSTRAINT uk_equipment UNIQUE (equipment_id),
    INDEX idx_team_id (team_id),
    CONSTRAINT fk_assignment_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材归属绑定表';

CREATE TABLE IF NOT EXISTS assignment_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL COMMENT '器材ID',
    old_team_id BIGINT COMMENT '原班组ID',
    new_team_id BIGINT NOT NULL COMMENT '新班组ID',
    change_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    operator VARCHAR(50) COMMENT '操作人',
    reason VARCHAR(200) COMMENT '变更原因',
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_change_time (change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归属变更记录表';

INSERT INTO team (team_name, member_count, description) VALUES 
('灭火一班', 12, '负责灭火训练'),
('灭火二班', 10, '负责灭火训练'),
('救援一班', 8, '负责救援训练'),
('通信一班', 6, '负责通信训练');

INSERT INTO equipment (equipment_code, training_purpose, size_spec) VALUES 
('EQ001', '灭火训练', '大型灭火器模型'),
('EQ002', '灭火训练', '小型灭火器模型'),
('EQ003', '救援训练', '救援绳索套装'),
('EQ004', '救援训练', '破拆工具套装'),
('EQ005', '通信训练', '对讲机模拟设备'),
('EQ006', '灭火训练', '消防栓模型'),
('EQ007', '救援训练', '急救箱模拟'),
('EQ008', '通信训练', '指挥中心控制台'),
('EQ009', '灭火训练', '水带模拟套装'),
('EQ010', '救援训练', '攀爬训练架');

INSERT INTO assignment (equipment_id, team_id, operator, remark) VALUES 
(1, 1, '管理员', '初始绑定'),
(2, 1, '管理员', '初始绑定'),
(3, 3, '管理员', '初始绑定'),
(4, 3, '管理员', '初始绑定'),
(5, 4, '管理员', '初始绑定'),
(6, 2, '管理员', '初始绑定'),
(7, 3, '管理员', '初始绑定'),
(8, 4, '管理员', '初始绑定');

INSERT INTO assignment_history (equipment_id, old_team_id, new_team_id, operator, reason) VALUES 
(1, NULL, 1, '管理员', '初始绑定'),
(2, NULL, 1, '管理员', '初始绑定'),
(3, NULL, 3, '管理员', '初始绑定'),
(4, NULL, 3, '管理员', '初始绑定'),
(5, NULL, 4, '管理员', '初始绑定'),
(6, NULL, 2, '管理员', '初始绑定'),
(7, NULL, 3, '管理员', '初始绑定'),
(8, NULL, 4, '管理员', '初始绑定');