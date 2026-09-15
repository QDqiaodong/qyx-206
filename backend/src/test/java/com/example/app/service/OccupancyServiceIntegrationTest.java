package com.example.app.service;

import com.example.app.dto.AssignmentAdjustDTO;
import com.example.app.dto.AssignmentDTO;
import com.example.app.dto.OccupancyCreateDTO;
import com.example.app.entity.Equipment;
import com.example.app.entity.Team;
import com.example.app.entity.TrainingOccupancy;
import com.example.app.exception.BusinessException;
import com.example.app.repository.EquipmentRepository;
import com.example.app.repository.TeamRepository;
import com.example.app.repository.TrainingOccupancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class OccupancyServiceIntegrationTest {

    @Autowired private OccupancyService occupancyService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TrainingOccupancyRepository occupancyRepository;

    private Long teamA;
    private Long teamB;
    private Long equipmentId;

    @BeforeEach
    void setUp() {
        occupancyRepository.deleteAll();

        Team a = new Team();
        a.setTeamName("测试一班" + System.nanoTime());
        a.setMemberCount(10);
        teamA = teamRepository.save(a).getId();

        Team b = new Team();
        b.setTeamName("测试二班" + System.nanoTime());
        b.setMemberCount(8);
        teamB = teamRepository.save(b).getId();

        Equipment eq = new Equipment();
        eq.setEquipmentCode("EQ-TEST-" + System.nanoTime());
        eq.setTrainingPurpose("灭火训练");
        eq.setSizeSpec("水带");
        equipmentId = equipmentRepository.save(eq).getId();

        AssignmentDTO bind = new AssignmentDTO();
        bind.setEquipmentId(equipmentId);
        bind.setTeamId(teamA);
        bind.setOperator("一班班长");
        assignmentService.bind(bind);
    }

    private OccupancyCreateDTO dto(Long teamId, LocalDate date, LocalTime start, LocalTime end, String course) {
        OccupancyCreateDTO d = new OccupancyCreateDTO();
        d.setEquipmentId(equipmentId);
        d.setTeamId(teamId);
        d.setTrainingDate(date);
        d.setCourseName(course);
        d.setStartTime(start);
        d.setEndTime(end);
        d.setOperator("班长");
        return d;
    }

    @Test
    void create_success_whenOwnedAndValid() {
        TrainingOccupancy o = occupancyService.create(
                dto(teamA, LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(10, 0), "水带铺设"));
        assertEquals(TrainingOccupancy.STATUS_ACTIVE, o.getStatus());
        assertEquals(1, occupancyService.countActiveForTeam(teamA, LocalDate.now()));
    }

    @Test
    void create_rejects_whenEndNotAfterStart() {
        BusinessException ex = assertThrows(BusinessException.class, () -> occupancyService.create(
                dto(teamA, LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(10, 0), "同刻")));
        assertTrue(ex.getMessage().contains("结束时间必须晚于开始时间"));
    }

    @Test
    void create_rejects_nonOwnedEquipment() {
        // 别的班组的班长来挂 -> 拒绝
        BusinessException ex = assertThrows(BusinessException.class, () -> occupancyService.create(
                dto(teamB, LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(10, 0), "抢器材")));
        assertTrue(ex.getMessage().contains("本班组"));
    }

    @Test
    void create_rejects_overlapButAllowsBackToBack() {
        occupancyService.create(dto(teamA, LocalDate.now(),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "第一节"));

        // 交叉：09:30-10:30
        assertThrows(BusinessException.class, () -> occupancyService.create(
                dto(teamA, LocalDate.now(), LocalTime.of(9, 30), LocalTime.of(10, 30), "交叉")));
        // 包含：08:00-11:00
        assertThrows(BusinessException.class, () -> occupancyService.create(
                dto(teamA, LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(11, 0), "包含")));

        // 首尾相接：10:00-11:00 不算交叉，应成功
        TrainingOccupancy next = occupancyService.create(dto(teamA, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "第二节"));
        assertNotNull(next.getId());
        assertEquals(2, occupancyService.countActiveForTeam(teamA, LocalDate.now()));
    }

    /**
     * 两个班长几乎同时给同一件器材挂交叉时段：
     * 器材行锁串行化，先落地者生效，后落地者报冲突 —— 恰好一条有效。
     */
    @Test
    void concurrentOverlap_exactlyOneWins() throws InterruptedException {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final LocalTime s = LocalTime.of(14, 0).plusMinutes(i * 15L);
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    occupancyService.create(dto(teamA, LocalDate.now(), s, LocalTime.of(16, 0), "并发课目"));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    conflict.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, success.get(), "先落地一条生效");
        assertEquals(1, conflict.get(), "后落地一条被挡");
        assertEquals(1, occupancyService.listActiveForEquipment(equipmentId, LocalDate.now()).size(),
                "不能两条都有效");
        assertEquals(1, occupancyService.countActiveForTeam(teamA, LocalDate.now()));
    }

    /**
     * 器材改班组：调整不被拦截；该件尚未结束的占用当场作废并留痕；
     * 已结束的不动；作废后不算班组有效数，也不再挡住新班组挂同时段。
     */
    @Test
    void adjustTeam_cancelsOpenOccupanciesAndFreesSlots() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        // 依赖“当天还有未到的时段”：保证 +3 小时不跨日、且 00:00-00:30 已成历史
        assumeTrue(now.isAfter(LocalTime.of(0, 30)) && now.isBefore(LocalTime.of(17, 57)),
                "当天已没有足够的未来时段，跳过墙钟相关用例");

        // 尚未结束：今天 2 小时后开始
        occupancyService.create(dto(teamA, today, now.plusHours(2), now.plusHours(3), "今天未结束"));
        // 明天的也未结束
        occupancyService.create(dto(teamA, today.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "明天课目"));
        // 今天凌晨已结束的不动
        occupancyService.create(dto(teamA, today,
                LocalTime.of(0, 0), LocalTime.of(0, 30), "今天已结束"));

        AssignmentAdjustDTO adjust = new AssignmentAdjustDTO();
        adjust.setEquipmentId(equipmentId);
        adjust.setNewTeamId(teamB);
        adjust.setOperator("管理员");
        adjust.setReason("统一调拨");
        assignmentService.adjust(adjust);

        // 班组A今天有效占用只剩已结束那条（作废不计）
        assertEquals(1, occupancyService.countActiveForTeam(teamA, today));

        var all = occupancyRepository.findAll();
        long cancelled = all.stream().filter(o -> TrainingOccupancy.STATUS_CANCELLED.equals(o.getStatus())).count();
        assertEquals(2, cancelled, "今天未结束 + 明天 共两条当场作废");

        all.stream().filter(o -> TrainingOccupancy.STATUS_CANCELLED.equals(o.getStatus())).forEach(o -> {
            assertNotNull(o.getCancelTime(), "留作废时间");
            assertEquals("管理员", o.getCancelOperator(), "留作废操作人");
            assertNotNull(o.getCancelReason(), "留作废原因");
            assertTrue(o.getCancelReason().contains("改归属班组"));
        });

        // 当日有效占用列表（不含作废）看不到作废记录
        var activePage = occupancyService.listForDay(today, null, null, false, PageRequest.of(0, 50));
        assertTrue(activePage.getContent().stream().noneMatch(o -> "CANCELLED".equals(o.getStatus())));
        assertEquals(1, activePage.getTotalElements());

        // 含作废时能看到痕迹
        var withCancelled = occupancyService.listForDay(today, null, null, true, PageRequest.of(0, 50));
        assertEquals(2, withCancelled.getTotalElements());

        // 器材已归班组B，B 挂与被作废时段完全相同的时间，不能再被挡住
        TrainingOccupancy rebook = occupancyService.create(
                dto(teamB, today, now.plusHours(2), now.plusHours(3), "二班接手"));
        assertEquals(TrainingOccupancy.STATUS_ACTIVE, rebook.getStatus());

        // A 再挂也会因为不再是名下器材而被拒
        assertThrows(BusinessException.class, () -> occupancyService.create(
                dto(teamA, today, now.plusHours(5), now.plusHours(6), "旧班组再挂")));
    }
}
