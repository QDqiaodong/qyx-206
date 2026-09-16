package com.example.app.service;

import com.example.app.dto.EquipmentDTO;
import com.example.app.entity.Equipment;
import com.example.app.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final RedisService redisService;

    public EquipmentService(EquipmentRepository equipmentRepository, RedisService redisService) {
        this.equipmentRepository = equipmentRepository;
        this.redisService = redisService;
    }

    public Page<Equipment> findAll(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return equipmentRepository.findByEquipmentCodeContainingOrTrainingPurposeContaining(keyword, keyword, pageable);
        }
        return equipmentRepository.findAll(pageable);
    }

    public Equipment findById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("器材不存在"));
    }

    @Transactional
    public Equipment create(EquipmentDTO dto) {
        if (equipmentRepository.existsByEquipmentCode(dto.getEquipmentCode())) {
            throw new RuntimeException("器材编号已存在");
        }
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode(dto.getEquipmentCode());
        equipment.setTrainingPurpose(dto.getTrainingPurpose());
        equipment.setSizeSpec(dto.getSizeSpec());
        Equipment saved = equipmentRepository.save(equipment);
        redisService.addEquipmentSpec(saved);
        return saved;
    }

    @Transactional
    public Equipment update(Long id, EquipmentDTO dto) {
        Equipment equipment = findById(id);
        if (!equipment.getEquipmentCode().equals(dto.getEquipmentCode()) &&
                equipmentRepository.existsByEquipmentCode(dto.getEquipmentCode())) {
            throw new RuntimeException("器材编号已存在");
        }
        equipment.setEquipmentCode(dto.getEquipmentCode());
        equipment.setTrainingPurpose(dto.getTrainingPurpose());
        equipment.setSizeSpec(dto.getSizeSpec());
        Equipment saved = equipmentRepository.save(equipment);
        redisService.updateEquipmentSpec(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Equipment equipment = findById(id);
        redisService.deleteEquipmentSpec(equipment.getEquipmentCode());
        equipmentRepository.deleteById(id);
    }

    public List<Equipment> findUnassigned() {
        return equipmentRepository.findUnassigned();
    }

    public List<Equipment> findByTeamId(Long teamId) {
        return equipmentRepository.findByTeamId(teamId);
    }

    public long count() {
        return equipmentRepository.count();
    }

    public List<Long> findAllIds() {
        return equipmentRepository.findAllIds();
    }
}