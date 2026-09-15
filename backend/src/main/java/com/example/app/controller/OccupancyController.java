package com.example.app.controller;

import com.example.app.dto.OccupancyCreateDTO;
import com.example.app.dto.OccupancyVO;
import com.example.app.entity.TrainingOccupancy;
import com.example.app.service.OccupancyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/occupancy")
@Slf4j
public class OccupancyController {

    private final OccupancyService occupancyService;

    public OccupancyController(OccupancyService occupancyService) {
        this.occupancyService = occupancyService;
    }

    /**
     * 班长挂课目占用；交叉冲突返回 400。
     */
    @PostMapping
    public ResponseEntity<TrainingOccupancy> create(@Valid @RequestBody OccupancyCreateDTO dto) {
        return ResponseEntity.ok(occupancyService.create(dto));
    }

    /**
     * 某日占用列表，可按班组/器材过滤；默认只看有效占用，includeCancelled=true 一并展示作废痕迹。
     */
    @GetMapping
    public ResponseEntity<Page<OccupancyVO>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDate,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(occupancyService.listForDay(
                trainingDate, teamId, equipmentId, includeCancelled, pageable));
    }

    /**
     * 某件器材某日的有效占用（挂占用前自查时段）。
     */
    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<List<OccupancyVO>> listForEquipment(
            @PathVariable Long equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDate) {
        return ResponseEntity.ok(occupancyService.listActiveForEquipment(equipmentId, trainingDate));
    }

    /**
     * 班组当天有效占用条数；可一次取全量，供班组侧看板使用（作废不计）。
     */
    @GetMapping("/team/{teamId}/active-count")
    public ResponseEntity<Map<String, Object>> teamActiveCount(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDate) {
        Map<String, Object> result = new HashMap<>();
        result.put("teamId", teamId);
        result.put("trainingDate", trainingDate);
        result.put("activeCount", occupancyService.countActiveForTeam(teamId, trainingDate));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/active-counts")
    public ResponseEntity<List<Map<String, Object>>> activeCounts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainingDate) {
        return ResponseEntity.ok(occupancyService.countActiveByTeam(trainingDate));
    }
}
