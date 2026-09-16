package com.example.app.controller;

import com.example.app.dto.ShuttleRunScoreDTO;
import com.example.app.dto.ShuttleRunScoreVO;
import com.example.app.service.ShuttleRunScoreService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 折返跑测验成绩。
 * 同一班组同一测验日只有一行：PUT 按 班组+测验日 整单保存，GET 实时算合格率。
 * 业务失败（合格人数比应测还多）返回 400；两人并发后写者返回 409，均不落库。
 */
@RestController
@RequestMapping("/api/shuttle-run")
@Slf4j
public class ShuttleRunScoreController {

    private final ShuttleRunScoreService scoreService;

    public ShuttleRunScoreController(ShuttleRunScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /** 某班组各测验日成绩（按测验日倒序），合格率随人数实时算出。 */
    @GetMapping("/team/{teamId}")
    public ResponseEntity<List<ShuttleRunScoreVO>> listByTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(scoreService.listByTeam(teamId));
    }

    /** 某班组某测验日唯一一行成绩；当天还没记时返回 204。 */
    @GetMapping("/team/{teamId}/date/{testDate}")
    public ResponseEntity<ShuttleRunScoreVO> getOne(
            @PathVariable Long teamId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testDate) {
        ShuttleRunScoreVO vo = scoreService.getOne(teamId, testDate);
        return vo == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(vo);
    }

    /** 总览：某测验日全部班组的应测/合格/合格率（含当天未记成绩的班组空位）。 */
    @GetMapping("/overview")
    public ResponseEntity<List<ShuttleRunScoreVO>> overview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testDate) {
        return ResponseEntity.ok(scoreService.overviewForDate(testDate));
    }

    /** 总览顶部合计：当天总应测、总合格、总合格率。 */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testDate) {
        return ResponseEntity.ok(scoreService.summaryForDate(testDate));
    }

    /**
     * 按 班组 + 测验日 整单保存成绩（首次为新增，当天改人数为带版本的更新）。
     * 合格人数比应测还多返回 400；被并发抢先返回 409；两种失败都整单不进库。
     */
    @PutMapping("/team/{teamId}/date/{testDate}")
    public ResponseEntity<ShuttleRunScoreVO> save(
            @PathVariable Long teamId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testDate,
            @Valid @RequestBody ShuttleRunScoreDTO dto) {
        return ResponseEntity.ok(scoreService.save(teamId, testDate, dto));
    }

    /**
     * 两个教员同时改同一班组同一日的合格人数：后写者被乐观锁整单拦下，
     * 不盖掉先写成功的人数，文案明确指向成绩（区别于班组档案的并发冲突）。
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ObjectOptimisticLockingFailureException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "该班组当天的折返跑成绩刚被其他人改过并已保存，请刷新后按最新人数重新修改");
        return ResponseEntity.status(409).body(body);
    }
}
