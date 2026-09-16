package com.example.app.service;

import com.example.app.dto.ShuttleRunScoreDTO;
import com.example.app.dto.ShuttleRunScoreVO;
import com.example.app.entity.ShuttleRunScore;
import com.example.app.entity.Team;
import com.example.app.exception.BusinessException;
import com.example.app.repository.ShuttleRunScoreRepository;
import com.example.app.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 折返跑测验成绩。
 *
 * 只有应测人数、合格人数落库；合格率（班组卡片、训练基地总览）一律不另存，
 * 任何读请求都按当前人数实时重算 —— 所以“改合格人数后两处合格率一起变、数字一样”
 * 是结构保证，而不是靠保存时多刷几个地方。
 *
 * 保存要么整单成功（人数、version 在同一条条件 UPDATE 里一起落），要么整单不进库：
 * - 应测人数改小到低于已写下的合格人数：抛业务异常回滚，库里人数、两处合格率仍是保存前的值；
 * - 两个教员同时改同一班组同一日：后落库者 version 条件匹配 0 行，整单 409，先写成功的不被盖掉。
 */
@Service
@Slf4j
public class ShuttleRunScoreService {

    private final ShuttleRunScoreRepository scoreRepository;
    private final TeamRepository teamRepository;

    public ShuttleRunScoreService(ShuttleRunScoreRepository scoreRepository,
                                  TeamRepository teamRepository) {
        this.scoreRepository = scoreRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * 按 班组 + 测验日 保存成绩：同一班组同一测验日只有一行，
     * 已有则整单更新，没有则新建。
     */
    @Transactional
    public ShuttleRunScoreVO save(Long teamId, LocalDate testDate, ShuttleRunScoreDTO dto) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("班组不存在"));
        if (testDate == null) {
            throw new BusinessException("测验日期不能为空");
        }

        int expected = dto.getExpectedCount() == null ? 0 : dto.getExpectedCount();
        int passed = dto.getPassedCount() == null ? 0 : dto.getPassedCount();
        if (expected < 0 || passed < 0) {
            throw new BusinessException("应测人数和合格人数不能为负");
        }
        // 合格人数比应测还多：无论是首次记成这样，还是应测改小后变成这样，一律整单不保存
        if (passed > expected) {
            throw new BusinessException(
                    "合格人数（" + passed + "）比应测人数（" + expected + "）还多，本次改人数不能保存");
        }

        Optional<ShuttleRunScore> existing = scoreRepository.findByTeamIdAndTestDate(teamId, testDate);
        if (existing.isEmpty()) {
            return createScore(team.getId(), testDate, expected, passed, dto.getOperator());
        }
        return updateScore(existing.get(), expected, passed, dto);
    }

    private ShuttleRunScoreVO createScore(Long teamId, LocalDate testDate,
                                          int expected, int passed, String operator) {
        // 两个教员同时给同一班组同一日记第一版：唯一键只放一行。
        // save 撞键时转成乐观锁冲突（409）——后到者整单不进库、不能冒出两行，
        // 刷新后走更新路径基于先记下的人数再改。
        try {
            ShuttleRunScore score = new ShuttleRunScore();
            score.setTeamId(teamId);
            score.setTestDate(testDate);
            score.setExpectedCount(expected);
            score.setPassedCount(passed);
            score.setOperator(operator);
            ShuttleRunScore saved = scoreRepository.saveAndFlush(score);
            return toVO(saved, teamName(teamId));
        } catch (DataIntegrityViolationException e) {
            throw new ObjectOptimisticLockingFailureException(
                    ShuttleRunScore.class, teamId + ":" + testDate);
        }
    }

    private ShuttleRunScoreVO updateScore(ShuttleRunScore current, int expected, int passed,
                                          ShuttleRunScoreDTO dto) {
        // 优先按前端打开成绩时读到的版本号比对；老客户端不带版本时用本事务读到的版本兜底
        Long expectedVersion = dto.getVersion() != null ? dto.getVersion() : current.getVersion();
        int updated = scoreRepository.updateVersioned(
                current.getId(), expected, passed, dto.getOperator(), expectedVersion);
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(ShuttleRunScore.class, current.getId());
        }
        ShuttleRunScore refreshed = scoreRepository.findById(current.getId()).orElseThrow();
        return toVO(refreshed, teamName(refreshed.getTeamId()));
    }

    @Transactional(readOnly = true)
    public List<ShuttleRunScoreVO> listByTeam(Long teamId) {
        if (teamRepository.findById(teamId).isEmpty()) {
            throw new BusinessException("班组不存在");
        }
        String teamName = teamName(teamId);
        return scoreRepository.findByTeamIdOrderByTestDateDesc(teamId).stream()
                .map(s -> toVO(s, teamName))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShuttleRunScoreVO getOne(Long teamId, LocalDate testDate) {
        return scoreRepository.findByTeamIdAndTestDate(teamId, testDate)
                .map(s -> toVO(s, teamName(teamId)))
                .orElse(null);
    }

    /**
     * 总览：某测验日每个班组一行，含实时合格率；当天没记成绩的班组也列出，计数字段为空。
     * 合格率全部在这里由应测/合格现算，与班组卡片是同一公式、同一本账。
     */
    @Transactional(readOnly = true)
    public List<ShuttleRunScoreVO> overviewForDate(LocalDate testDate) {
        Map<Long, ShuttleRunScore> byTeam = new LinkedHashMap<>();
        for (ShuttleRunScore s : scoreRepository.findByTestDate(testDate)) {
            byTeam.put(s.getTeamId(), s);
        }
        List<ShuttleRunScoreVO> result = new ArrayList<>();
        for (Team team : teamRepository.findAll()) {
            ShuttleRunScore s = byTeam.get(team.getId());
            if (s == null) {
                ShuttleRunScoreVO empty = new ShuttleRunScoreVO();
                empty.setTeamId(team.getId());
                empty.setTeamName(team.getTeamName());
                empty.setTestDate(testDate);
                result.add(empty);
            } else {
                result.add(toVO(s, team.getTeamName()));
            }
        }
        return result;
    }

    /**
     * 训练基地总览当天的合计：总应测、总合格、总合格率（百分比保留 1 位小数）。
     * 只合计当天记了成绩的班组，与班组卡片逐行数字同源；保存失败整单回滚时，
     * 这里下一次读到的仍是保存前的人数与合格率。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summaryForDate(LocalDate testDate) {
        long totalExpected = 0;
        long totalPassed = 0;
        for (ShuttleRunScore s : scoreRepository.findByTestDate(testDate)) {
            totalExpected += s.getExpectedCount();
            totalPassed += s.getPassedCount();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("testDate", testDate);
        summary.put("totalExpected", totalExpected);
        summary.put("totalPassed", totalPassed);
        summary.put("passRate", passRate(totalExpected, totalPassed));
        return summary;
    }

    /**
     * 合格率唯一口径：合格 / 应测 × 100，保留 1 位小数；应测为 0 时无可测人数，记为 null。
     */
    private static Double passRate(long expected, long passed) {
        if (expected <= 0) {
            return null;
        }
        return Math.round((double) passed / expected * 1000) / 10.0;
    }

    private ShuttleRunScoreVO toVO(ShuttleRunScore s, String teamName) {
        ShuttleRunScoreVO vo = new ShuttleRunScoreVO();
        vo.setId(s.getId());
        vo.setTeamId(s.getTeamId());
        vo.setTeamName(teamName);
        vo.setTestDate(s.getTestDate());
        vo.setExpectedCount(s.getExpectedCount());
        vo.setPassedCount(s.getPassedCount());
        vo.setPassRate(passRate(s.getExpectedCount(), s.getPassedCount()));
        vo.setOperator(s.getOperator());
        vo.setVersion(s.getVersion());
        return vo;
    }

    private String teamName(Long teamId) {
        return teamRepository.findById(teamId).map(Team::getTeamName).orElse("已删除");
    }
}
