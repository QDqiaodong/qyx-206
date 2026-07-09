package com.example.app.controller;

import com.example.app.dto.TeamDTO;
import com.example.app.entity.Team;
import com.example.app.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team")
@Slf4j
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<Page<Team>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return ResponseEntity.ok(teamService.findAll(keyword, pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Team>> getAll() {
        return ResponseEntity.ok(teamService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Team> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Team> create(@Valid @RequestBody TeamDTO dto) {
        return ResponseEntity.ok(teamService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Team> update(@PathVariable Long id, @Valid @RequestBody TeamDTO dto) {
        return ResponseEntity.ok(teamService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return ResponseEntity.noContent().build();
    }
}