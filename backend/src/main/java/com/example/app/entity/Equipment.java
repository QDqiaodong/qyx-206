package com.example.app.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment", indexes = {
        @Index(name = "idx_equipment_code", columnList = "equipmentCode")
})
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_code", unique = true, nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "training_purpose", nullable = false, length = 100)
    private String trainingPurpose;

    @Column(name = "size_spec", length = 100)
    private String sizeSpec;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}