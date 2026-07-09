package com.example.app.controller;

import com.example.app.dto.TeamStatisticsDTO;
import com.example.app.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@Slf4j
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(statisticsService.getOverview());
    }

    @GetMapping("/team")
    public ResponseEntity<List<TeamStatisticsDTO>> getTeamStatistics() {
        return ResponseEntity.ok(statisticsService.getTeamStatistics());
    }
}