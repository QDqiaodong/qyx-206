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
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本号：两人同时改同一班时只留一次完整保存',
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

-- 课目占用：班长把本班组名下器材挂到某个训练日的课目时段
-- status: ACTIVE 有效 / CANCELLED 已作废（器材改班组时未结束的占用当场作废）
-- 交叉冲突在应用层通过器材行锁串行化保证，不作唯一索引
CREATE TABLE IF NOT EXISTS training_occupancy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL COMMENT '器材ID',
    team_id BIGINT NOT NULL COMMENT '班组ID',
    training_date DATE NOT NULL COMMENT '训练日期',
    start_time TIME NOT NULL COMMENT '开始时间',
    end_time TIME NOT NULL COMMENT '结束时间（必须晚于开始时间）',
    course_name VARCHAR(100) NOT NULL COMMENT '课目名称',
    operator VARCHAR(50) COMMENT '挂载操作人（班长）',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/CANCELLED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '挂载时间',
    cancel_time DATETIME NULL COMMENT '作废时间',
    cancel_operator VARCHAR(50) NULL COMMENT '作废操作人',
    cancel_reason VARCHAR(200) NULL COMMENT '作废原因（改班作废留痕）',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_occupancy_equipment_day (equipment_id, training_date),
    INDEX idx_occupancy_team_day (team_id, training_date),
    INDEX idx_occupancy_status (status),
    CONSTRAINT fk_occupancy_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE,
    CONSTRAINT fk_occupancy_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课目训练器材占用表';

-- 器材外借离场：班长把本班组名下已归属器材拉出营区演练时的离场登记
-- status: OUT 在外未还 / OVERDUE 超期未还（定时扫描翻状态并留痕）/ RETURNED 已归还
-- “同一件器材在外未还不能再开第二条”在应用层通过器材行锁串行化保证，与课目占用同一把锁，不作唯一索引
-- 离场与课目占用两本账互不回写：出门只读取占用做前置拦截，绝不改占用、不作废时段
CREATE TABLE IF NOT EXISTS equipment_loan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL COMMENT '器材ID',
    team_id BIGINT NOT NULL COMMENT '出门时所属班组ID（快照）',
    checkout_time DATETIME NOT NULL COMMENT '出门时间',
    expected_return_time DATETIME NOT NULL COMMENT '预计归还时刻（必填）',
    companions VARCHAR(500) NOT NULL COMMENT '同行人（必填）',
    reason VARCHAR(500) NOT NULL COMMENT '离场事由（必填）',
    operator VARCHAR(50) COMMENT '登记班长',
    status VARCHAR(20) NOT NULL DEFAULT 'OUT' COMMENT '状态：OUT/OVERDUE/RETURNED',
    overdue_time DATETIME NULL COMMENT '翻成超期的时间',
    return_time DATETIME NULL COMMENT '实际归还时间',
    return_operator VARCHAR(50) NULL COMMENT '归还经手人',
    return_remark VARCHAR(200) NULL COMMENT '归还备注',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_loan_equipment (equipment_id),
    INDEX idx_loan_team (team_id),
    INDEX idx_loan_status (status),
    INDEX idx_loan_expected_return (expected_return_time),
    CONSTRAINT fk_loan_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材外借离场登记表';

-- 折返跑测验成绩：同一班组同一测验日只允许一行（纸上先记一版、当天改人数走同一行更新）
-- 合格率不入库：班组卡片与训练基地总览都按 合格人数/应测人数 实时重算，两处永远同一本账
-- 两人同时改同一班组同一日：version 条件更新只放后写者之外的第一版，后写者整单 409，不盖先写者
CREATE TABLE IF NOT EXISTS shuttle_run_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL COMMENT '班组ID',
    test_date DATE NOT NULL COMMENT '测验日期',
    expected_count INT NOT NULL COMMENT '应测人数',
    passed_count INT NOT NULL COMMENT '合格人数（不得大于应测人数）',
    operator VARCHAR(50) COMMENT '登记/修改的教员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近改人数时间',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本号：并发改人数时后写者整单被拒',
    UNIQUE KEY uk_shuttle_team_date (team_id, test_date),
    INDEX idx_shuttle_date (test_date),
    CONSTRAINT fk_shuttle_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='折返跑测验成绩表（班组+测验日唯一）';


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