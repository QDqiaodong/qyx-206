package com.example.app.controller;

import com.example.app.dto.EquipmentDTO;
import com.example.app.entity.Equipment;
import com.example.app.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/equipment")
@Slf4j
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ResponseEntity<Page<Equipment>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return ResponseEntity.ok(equipmentService.findAll(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Equipment> getById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Equipment> create(@Valid @RequestBody EquipmentDTO dto) {
        return ResponseEntity.ok(equipmentService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> update(@PathVariable Long id, @Valid @RequestBody EquipmentDTO dto) {
        return ResponseEntity.ok(equipmentService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}