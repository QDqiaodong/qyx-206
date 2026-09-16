package com.example.app.service;

import com.example.app.dto.TeamDTO;
import com.example.app.entity.Team;
import com.example.app.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 班组档案并发保存：两个人几乎同时改同一个班（一个改编制、一个改职责说明），
 * 只允许先完整落库的那一版留下；后落库者整单被乐观锁拒绝，
 * 库里不许出现“编制来自 A、说明来自 B”的拼版。
 *
 * 另覆盖：档案保存后，总览统计与 /team/all 下拉读路径立刻读到同一版。
 */
@SpringBootTest
class TeamServiceConcurrencyIntegrationTest {

    @Autowired private TeamService teamService;
    @Autowired private StatisticsService statisticsService;
    @Autowired private TeamRepository teamRepository;

    private Long teamId;

    @BeforeEach
    void setUp() {
        Team team = new Team();
        team.setTeamName("并发测试班" + System.nanoTime());
        team.setMemberCount(10);
        team.setDescription("旧职责");
        teamId = teamRepository.save(team).getId();
    }

    @Test
    void update_isImmediatelyVisibleOnStatisticsAndDropdownReadPaths() {
        TeamDTO dto = new TeamDTO();
        dto.setTeamName(teamRepository.findById(teamId).orElseThrow().getTeamName());
        dto.setMemberCount(18);
        dto.setDescription("新职责说明");

        teamService.update(teamId, dto);

        // 总览卡片读路径（/api/statistics/team 背后的服务）
        var stat = statisticsService.getTeamStatistics().stream()
                .filter(s -> s.getTeamId().equals(teamId))
                .findFirst()
                .orElseThrow();
        assertEquals(18, stat.getMemberCount());
        assertEquals("新职责说明", stat.getDescription());

        // 归属绑定等下拉读路径（/api/team/all）
        Team viaDropdown = teamService.findAll().stream()
                .filter(t -> t.getId().equals(teamId))
                .findFirst()
                .orElseThrow();
        assertEquals(18, viaDropdown.getMemberCount());
        assertEquals("新职责说明", viaDropdown.getDescription());
    }

    @Test
    void concurrentEdits_onlyOneCompleteSaveSurvives_noMixedFields() throws Exception {
        // 两人都基于同一份旧档案（version=0）各改一个字段
        Team base = teamService.findById(teamId);
        assertEquals(0L, base.getVersion());

        TeamDTO changeMemberCount = new TeamDTO();
        changeMemberCount.setTeamName(base.getTeamName());
        changeMemberCount.setMemberCount(25);          // A：只改编制
        changeMemberCount.setDescription("旧职责");      // A 表单里说明仍是旧值
        changeMemberCount.setVersion(0L);

        TeamDTO changeDescription = new TeamDTO();
        changeDescription.setTeamName(base.getTeamName());
        changeDescription.setMemberCount(10);           // B：只改说明，编制表单里仍是旧值
        changeDescription.setDescription("B改后的职责");
        changeDescription.setVersion(0L);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        Runnable editA = () -> runConcurrentEdit(changeMemberCount, ready, start, success, conflict);
        Runnable editB = () -> runConcurrentEdit(changeDescription, ready, start, success, conflict);

        Thread t1 = new Thread(editA, "edit-member-count");
        Thread t2 = new Thread(editB, "edit-description");
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertEquals(1, success.get(), "两次并发保存必须恰好一次落库");
        assertEquals(1, conflict.get(), "后落库者必须被整单拒绝");

        Team winner = teamService.findById(teamId);
        assertEquals(1L, winner.getVersion(), "版本号只该被顶一次");
        // 无论谁先，留下的都必须是一次完整保存：编制和说明同属一版，不能拼成 25 + B的说明
        if (winner.getMemberCount() == 25) {
            assertEquals("旧职责", winner.getDescription(), "A 整单胜出时说明必须是 A 表单里的值");
        } else {
            assertEquals(10, winner.getMemberCount(), "B 整单胜出时编制必须是 B 表单里的值");
            assertEquals("B改后的职责", winner.getDescription());
        }

        // 被拒方带着新表单基于最新版本重提，应当成功（用户刷新后重填路径）
        Team refreshed = teamService.findById(teamId);
        TeamDTO retry = new TeamDTO();
        retry.setTeamName(refreshed.getTeamName());
        retry.setMemberCount(25);
        retry.setDescription(refreshed.getDescription());
        retry.setVersion(refreshed.getVersion());
        teamService.update(teamId, retry);
        Team afterRetry = teamService.findById(teamId);
        assertEquals(25, afterRetry.getMemberCount());
        assertEquals(2L, afterRetry.getVersion());
    }

    @Test
    void update_withStaleVersion_isRejectedEvenWhenOnlyOneRequest() {
        TeamDTO dto = new TeamDTO();
        dto.setTeamName(teamService.findById(teamId).getTeamName());
        dto.setMemberCount(30);
        dto.setDescription("先保存的一版");
        dto.setVersion(0L);
        teamService.update(teamId, dto);
        assertEquals(1L, teamService.findById(teamId).getVersion());

        TeamDTO stale = new TeamDTO();
        stale.setTeamName(dto.getTeamName());
        stale.setMemberCount(99);
        stale.setDescription("拿着旧表单再保存");
        stale.setVersion(0L);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> teamService.update(teamId, stale));

        Team untouched = teamService.findById(teamId);
        assertEquals(30, untouched.getMemberCount());
        assertEquals("先保存的一版", untouched.getDescription());
        assertEquals(1L, untouched.getVersion());
    }

    private void runConcurrentEdit(TeamDTO dto, CountDownLatch ready, CountDownLatch start,
                                   AtomicInteger success, AtomicInteger conflict) {
        try {
            ready.countDown();
            start.await();
            teamService.update(teamId, dto);
            success.incrementAndGet();
        } catch (ObjectOptimisticLockingFailureException e) {
            conflict.incrementAndGet();
        } catch (Exception e) {
            fail("并发保存只应出现成功或乐观锁冲突，实际：" + e, e);
        }
    }
}
