package com.example.app.controller;

import com.example.app.dto.AssignmentAdjustDTO;
import com.example.app.dto.AssignmentDTO;
import com.example.app.dto.AssignmentIntervalVO;
import com.example.app.dto.IntervalMigrationResult;
import com.example.app.dto.OwnershipMismatchVO;
import com.example.app.entity.Assignment;
import com.example.app.entity.Equipment;
import com.example.app.service.AssignmentService;
import com.example.app.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignment")
@Slf4j
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final EquipmentService equipmentService;

    public AssignmentController(AssignmentService assignmentService, EquipmentService equipmentService) {
        this.assignmentService = assignmentService;
        this.equipmentService = equipmentService;
    }

    @PostMapping("/bind")
    public ResponseEntity<Assignment> bind(@Valid @RequestBody AssignmentDTO dto) {
        return ResponseEntity.ok(assignmentService.bind(dto));
    }

    @PutMapping("/adjust")
    public ResponseEntity<Assignment> adjust(@Valid @RequestBody AssignmentAdjustDTO dto) {
        return ResponseEntity.ok(assignmentService.adjust(dto));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(assignmentService.getHistory(pageable));
    }

    /**
     * 一件器材的归属区间时间线：每段归谁、从何时到何时、待了多久。
     */
    @GetMapping("/equipment/{equipmentId}/intervals")
    public ResponseEntity<List<AssignmentIntervalVO>> getEquipmentIntervals(@PathVariable Long equipmentId) {
        return ResponseEntity.ok(assignmentService.getEquipmentIntervals(equipmentId));
    }

    /**
     * 对账：当前归属与流水末段对不上的器材清单（只列账，不改账）。
     */
    @GetMapping("/ownership-mismatches")
    public ResponseEntity<List<OwnershipMismatchVO>> getOwnershipMismatches() {
        return ResponseEntity.ok(assignmentService.findOwnershipMismatches());
    }

    /**
     * 老流水一次性推区间；推不动的器材逐件列明原因返回，不按猜的补。
     */
    @PostMapping("/history/migrate-intervals")
    public ResponseEntity<IntervalMigrationResult> migrateIntervals() {
        return ResponseEntity.ok(assignmentService.migrateLegacyIntervals());
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<List<Equipment>> getTeamEquipments(@PathVariable Long teamId) {
        return ResponseEntity.ok(assignmentService.getTeamEquipments(teamId));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<List<Equipment>> getUnassigned() {
        return ResponseEntity.ok(equipmentService.findUnassigned());
    }
}