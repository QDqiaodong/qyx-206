package com.example.app.dto;

public class OverviewStatisticsDTO {
    private Long totalEquipment;
    private Long totalTeam;
    private Double averageEquipmentPerTeam;

    public Long getTotalEquipment() {
        return totalEquipment;
    }

    public void setTotalEquipment(Long totalEquipment) {
        this.totalEquipment = totalEquipment;
    }

    public Long getTotalTeam() {
        return totalTeam;
    }

    public void setTotalTeam(Long totalTeam) {
        this.totalTeam = totalTeam;
    }

    public Double getAverageEquipmentPerTeam() {
        return averageEquipmentPerTeam;
    }

    public void setAverageEquipmentPerTeam(Double averageEquipmentPerTeam) {
        this.averageEquipmentPerTeam = averageEquipmentPerTeam;
    }
}