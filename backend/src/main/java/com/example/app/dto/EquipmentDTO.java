package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;

public class EquipmentDTO {

    private Long id;

    @NotBlank(message = "器材编号不能为空")
    private String equipmentCode;

    @NotBlank(message = "训练用途不能为空")
    private String trainingPurpose;

    private String sizeSpec;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public String getTrainingPurpose() {
        return trainingPurpose;
    }

    public void setTrainingPurpose(String trainingPurpose) {
        this.trainingPurpose = trainingPurpose;
    }

    public String getSizeSpec() {
        return sizeSpec;
    }

    public void setSizeSpec(String sizeSpec) {
        this.sizeSpec = sizeSpec;
    }
}