package com.example.app.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 老流水一次性推区间的结果：推成了几件几段；推不动的逐件列明原因，
 * 绝不按猜的补 —— 留在老格式里等人工核对。
 */
public class IntervalMigrationResult {

    private int legacyEquipmentCount;
    private int migratedEquipmentCount;
    private int migratedSegmentCount;
    private final List<Problem> problems = new ArrayList<>();

    public int getLegacyEquipmentCount() {
        return legacyEquipmentCount;
    }

    public void setLegacyEquipmentCount(int legacyEquipmentCount) {
        this.legacyEquipmentCount = legacyEquipmentCount;
    }

    public int getMigratedEquipmentCount() {
        return migratedEquipmentCount;
    }

    public void setMigratedEquipmentCount(int migratedEquipmentCount) {
        this.migratedEquipmentCount = migratedEquipmentCount;
    }

    public int getMigratedSegmentCount() {
        return migratedSegmentCount;
    }

    public void setMigratedSegmentCount(int migratedSegmentCount) {
        this.migratedSegmentCount = migratedSegmentCount;
    }

    public List<Problem> getProblems() {
        return problems;
    }

    public void addProblem(Long equipmentId, String equipmentCode, String reason) {
        problems.add(new Problem(equipmentId, equipmentCode, reason));
    }

    public static class Problem {
        private final Long equipmentId;
        private final String equipmentCode;
        private final String reason;

        public Problem(Long equipmentId, String equipmentCode, String reason) {
            this.equipmentId = equipmentId;
            this.equipmentCode = equipmentCode;
            this.reason = reason;
        }

        public Long getEquipmentId() {
            return equipmentId;
        }

        public String getEquipmentCode() {
            return equipmentCode;
        }

        public String getReason() {
            return reason;
        }
    }
}
