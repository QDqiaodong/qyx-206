package com.example.app.service;

import com.example.app.entity.Equipment;
import com.example.app.repository.EquipmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RedisService {

    private static final String EQUIPMENT_SPEC_HASH = "equipment:spec";

    private final RedisTemplate<String, Object> redisTemplate;
    private final EquipmentRepository equipmentRepository;

    public RedisService(RedisTemplate<String, Object> redisTemplate, EquipmentRepository equipmentRepository) {
        this.redisTemplate = redisTemplate;
        this.equipmentRepository = equipmentRepository;
    }

    public void refreshEquipmentSpecCache() {
        try {
            List<Equipment> equipments = equipmentRepository.findAll();
            HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
            
            Map<Object, Object> existing = hashOps.entries(EQUIPMENT_SPEC_HASH);
            if (!existing.isEmpty()) {
                hashOps.delete(EQUIPMENT_SPEC_HASH);
            }
            
            for (Equipment equipment : equipments) {
                hashOps.put(EQUIPMENT_SPEC_HASH, equipment.getEquipmentCode(), 
                    equipment.getSizeSpec() + ":" + equipment.getTrainingPurpose());
            }
            log.info("Equipment spec cache refreshed, total: {}", equipments.size());
        } catch (Exception e) {
            log.warn("Failed to refresh equipment spec cache: {}", e.getMessage());
            log.debug("Redis exception stack trace:", e);
        }
    }

    public String getEquipmentSpec(String equipmentCode) {
        try {
            HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
            Object result = hashOps.get(EQUIPMENT_SPEC_HASH, equipmentCode);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to get equipment spec from cache: {}", e.getMessage());
            return null;
        }
    }

    public void addEquipmentSpec(Equipment equipment) {
        try {
            HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
            hashOps.put(EQUIPMENT_SPEC_HASH, equipment.getEquipmentCode(),
                equipment.getSizeSpec() + ":" + equipment.getTrainingPurpose());
        } catch (Exception e) {
            log.warn("Failed to add equipment spec to cache: {}", e.getMessage());
        }
    }

    public void updateEquipmentSpec(Equipment equipment) {
        addEquipmentSpec(equipment);
    }

    public void deleteEquipmentSpec(String equipmentCode) {
        try {
            HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
            hashOps.delete(EQUIPMENT_SPEC_HASH, equipmentCode);
        } catch (Exception e) {
            log.warn("Failed to delete equipment spec from cache: {}", e.getMessage());
        }
    }

    public Map<Object, Object> getAllEquipmentSpecs() {
        try {
            HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
            return hashOps.entries(EQUIPMENT_SPEC_HASH);
        } catch (Exception e) {
            log.warn("Failed to get all equipment specs from cache: {}", e.getMessage());
            return Map.of();
        }
    }
}