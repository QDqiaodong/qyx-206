package com.example.app.service;

import com.example.app.dto.ShuttleRunScoreDTO;
import com.example.app.dto.ShuttleRunScoreVO;
import com.example.app.entity.ShuttleRunScore;
import com.example.app.entity.Team;
import com.example.app.exception.BusinessException;
import com.example.app.repository.ShuttleRunScoreRepository;
import com.example.app.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 折返跑测验成绩规则：
 * 1. 同一班组同一测验日只留一行，改人数走同一行更新，合格率随新人数实时重算；
 * 2. 班组卡片与训练基地总览两处合格率同源同值；
 * 3. 应测人数改小到低于已写下的合格人数时整单不保存，并明示合格人数比应测还多，
 *    保存失败后两处合格率仍是保存前的值；
 * 4. 两人同时改同一班组同一日的合格人数，恰好一次落库，后写者被 409 整单拦下，
 *    先写成功的人数不被盖掉。
 */
@SpringBootTest
class ShuttleRunScoreServiceIntegrationTest {

    @Autowired private ShuttleRunScoreService scoreService;
    @Autowired private ShuttleRunScoreRepository scoreRepository;
    @Autowired private TeamRepository teamRepository;

    private Long teamId;
    private final LocalDate day = LocalDate.of(2026, 9, 16);

    @BeforeEach
    void setUp() {
        scoreRepository.deleteAll();
        Team team = new Team();
        team.setTeamName("折返跑测试班" + System.nanoTime());
        team.setMemberCount(10);
        teamId = teamRepository.save(team).getId();
    }

    private ShuttleRunScoreDTO dto(int expected, int passed) {
        ShuttleRunScoreDTO dto = new ShuttleRunScoreDTO();
        dto.setTestDate(day);
        dto.setExpectedCount(expected);
        dto.setPassedCount(passed);
        dto.setOperator("教员");
        return dto;
    }

    private ShuttleRunScoreVO teamCard() {
        return scoreService.getOne(teamId, day);
    }

    private ShuttleRunScoreVO overviewRow() {
        return scoreService.overviewForDate(day).stream()
                .filter(r -> teamId.equals(r.getTeamId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("总览里找不到该班组"));
    }

    // ---------- 1. 同班组同日只留一行；改人数后两处合格率一起按新人数重算且相等 ----------

    @Test
    void save_sameTeamSameDay_keepsSingleRow_andBothPassRatesRecomputeAndMatch() {
        scoreService.save(teamId, day, dto(10, 5));
        assertEquals(1, scoreRepository.count(), "首次记录只该有一行");
        assertEquals(50.0, teamCard().getPassRate());
        assertEquals(50.0, overviewRow().getPassRate());

        // 当天改合格人数：5 -> 8，走同一行更新而不是新增一行
        ShuttleRunScoreVO first = teamCard();
        ShuttleRunScoreDTO changed = dto(10, 8);
        changed.setVersion(first.getVersion());
        ShuttleRunScoreVO updated = scoreService.save(teamId, day, changed);

        assertEquals(1, scoreRepository.count(), "同一班组同一测验日改人数后仍只能有一行");
        assertEquals(first.getId(), updated.getId(), "改人数必须落在原来那一行上");
        assertEquals(8, updated.getPassedCount());
        assertEquals(80.0, updated.getPassRate(), "合格率要按新合格人数重算 8/10");
        assertEquals(80.0, teamCard().getPassRate(), "班组卡片合格率必须是新值");
        assertEquals(80.0, overviewRow().getPassRate(), "总览合格率必须与班组卡片一致");
        assertEquals(1L, updated.getVersion(), "版本号被顶一次");
    }

    // ---------- 2. 应测改小到低于已写下的合格人数：不保存，两处合格率停在保存前 ----------

    @Test
    void save_expectedBelowPassed_isRejected_andRatesStayAtPreviousValueEverywhere() {
        scoreService.save(teamId, day, dto(10, 8));
        assertEquals(80.0, teamCard().getPassRate());

        ShuttleRunScoreDTO shrink = dto(6, 8); // 应测改小到 6，合格已写下 8
        shrink.setVersion(0L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreService.save(teamId, day, shrink));
        assertTrue(ex.getMessage().contains("合格人数") && ex.getMessage().contains("应测人数"),
                "必须写明合格人数比应测还多，实际：" + ex.getMessage());

        // 整单没落库：人数与两处合格率都停在保存前
        ShuttleRunScore row = scoreRepository.findByTeamIdAndTestDate(teamId, day).orElseThrow();
        assertEquals(10, row.getExpectedCount());
        assertEquals(8, row.getPassedCount());
        assertEquals(0L, row.getVersion());
        assertEquals(80.0, teamCard().getPassRate(), "保存失败，班组卡片合格率必须仍是保存前的值");
        assertEquals(80.0, overviewRow().getPassRate(), "保存失败，总览合格率也必须仍是保存前的值");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = scoreService.summaryForDate(day);
        assertEquals(10L, ((Number) summary.get("totalExpected")).longValue());
        assertEquals(8L, ((Number) summary.get("totalPassed")).longValue());
        assertEquals(80.0, ((Number) summary.get("passRate")).doubleValue());
    }

    @Test
    void save_passedGreaterThanExpectedOnFirstEntry_isRejected() {
        assertThrows(BusinessException.class, () -> scoreService.save(teamId, day, dto(5, 6)));
        assertEquals(0, scoreRepository.count(), "首记就合格多于应测时不能落任何行");
        assertNull(teamCard());
    }

    // ---------- 3. 两人同时改合格人数：恰好一次落库，后写者不能盖掉先写成功者 ----------

    @Test
    void concurrentPassedEdits_onlyOneWins_loserGetsConflict_andWinnerStays() throws Exception {
        scoreService.save(teamId, day, dto(10, 5));
        assertEquals(0L, teamCard().getVersion());

        // 两个教员都基于 version=0 的同一版各改各的合格人数
        ShuttleRunScoreDTO writeA = dto(10, 9);
        writeA.setVersion(0L);
        ShuttleRunScoreDTO writeB = dto(10, 2);
        writeB.setVersion(0L);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        Runnable editA = () -> runConcurrentSave(writeA, ready, start, success, conflict);
        Runnable editB = () -> runConcurrentSave(writeB, ready, start, success, conflict);
        Thread t1 = new Thread(editA, "shuttle-pass-9");
        Thread t2 = new Thread(editB, "shuttle-pass-2");
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertEquals(1, success.get(), "两次并发保存必须恰好一次落库");
        assertEquals(1, conflict.get(), "后写者必须被整单拒绝");

        ShuttleRunScore winner = scoreRepository.findByTeamIdAndTestDate(teamId, day).orElseThrow();
        assertEquals(1L, winner.getVersion(), "版本号只该被顶一次");
        assertTrue(winner.getPassedCount() == 9 || winner.getPassedCount() == 2);
        int winnerPassed = winner.getPassedCount();

        // 先写成功的人数不能被后写者盖掉：两处读路径看到的都是胜出值
        assertEquals(winnerPassed, teamCard().getPassedCount());
        assertEquals(winnerPassed, overviewRow().getPassedCount());
        double expectedRate = winnerPassed == 9 ? 90.0 : 20.0;
        assertEquals(expectedRate, teamCard().getPassRate());
        assertEquals(expectedRate, overviewRow().getPassRate());

        // 被拒教员刷新拿到最新版本后重改，可以成功
        ShuttleRunScoreDTO retry = dto(10, 7);
        retry.setVersion(1L);
        scoreService.save(teamId, day, retry);
        assertEquals(7, teamCard().getPassedCount());
        assertEquals(70.0, teamCard().getPassRate());
        assertEquals(70.0, overviewRow().getPassRate());
        assertEquals(2L, teamCard().getVersion());
    }

    @Test
    void save_withStaleVersion_isRejectedAlone() {
        scoreService.save(teamId, day, dto(10, 5));
        ShuttleRunScoreDTO second = dto(10, 6);
        second.setVersion(0L);
        scoreService.save(teamId, day, second);

        ShuttleRunScoreDTO stale = dto(10, 7);
        stale.setVersion(0L);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> scoreService.save(teamId, day, stale));
        assertEquals(6, teamCard().getPassedCount(), "旧版本号的保存整单不进库");
    }

    // ---------- 4. 总览合计与未记成绩班组 ----------

    @Test
    void overview_listsTeamWithoutScore_asEmpty_andSummaryCountsOnlyScoredTeams() {
        Team other = new Team();
        other.setTeamName("折返跑空白班" + System.nanoTime());
        other.setMemberCount(6);
        Long otherId = teamRepository.save(other).getId();

        scoreService.save(teamId, day, dto(10, 8));

        List<ShuttleRunScoreVO> rows = scoreService.overviewForDate(day);
        ShuttleRunScoreVO scored = rows.stream().filter(r -> teamId.equals(r.getTeamId())).findFirst().orElseThrow();
        ShuttleRunScoreVO blank = rows.stream().filter(r -> otherId.equals(r.getTeamId())).findFirst().orElseThrow();
        assertEquals(10, scored.getExpectedCount());
        assertEquals(8, scored.getPassedCount());
        assertEquals(80.0, scored.getPassRate());
        assertNull(blank.getExpectedCount(), "当天没记成绩的班组应测/合格/合格率都应为空");
        assertNull(blank.getPassedCount());
        assertNull(blank.getPassRate());

        Map<String, Object> summary = scoreService.summaryForDate(day);
        assertEquals(10L, ((Number) summary.get("totalExpected")).longValue(), "合计只数记了成绩的班组");
        assertEquals(8L, ((Number) summary.get("totalPassed")).longValue());
        assertEquals(80.0, ((Number) summary.get("passRate")).doubleValue());
    }

    @Test
    void differentDays_areIndependentRows() {
        LocalDate anotherDay = day.plusDays(1);
        scoreService.save(teamId, day, dto(10, 5));
        scoreService.save(teamId, anotherDay, dto(8, 8));
        assertEquals(2, scoreRepository.count());
        assertEquals(50.0, scoreService.getOne(teamId, day).getPassRate());
        assertEquals(100.0, scoreService.getOne(teamId, anotherDay).getPassRate());
    }

    private void runConcurrentSave(ShuttleRunScoreDTO dto, CountDownLatch ready, CountDownLatch start,
                                   AtomicInteger success, AtomicInteger conflict) {
        try {
            ready.countDown();
            start.await();
            scoreService.save(teamId, day, dto);
            success.incrementAndGet();
        } catch (ObjectOptimisticLockingFailureException e) {
            conflict.incrementAndGet();
        } catch (Exception e) {
            fail("并发保存只应出现成功或乐观锁冲突，实际：" + e, e);
        }
    }
}
