package com.example.app.controller;

import com.example.app.entity.Team;
import com.example.app.repository.ShuttleRunScoreRepository;
import com.example.app.repository.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 折返跑成绩 HTTP 层：合格多于应测返回 400 且整单不落库；
 * 两个教员并发改人数时一人 200 一人 409，先写成功的人数留下；
 * 班组行与总览合计的合格率始终一致。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShuttleRunScoreControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TeamRepository teamRepository;
    @Autowired private ShuttleRunScoreRepository scoreRepository;

    private Long teamId;
    private final String day = "2026-09-16";

    @BeforeEach
    void setUp() {
        scoreRepository.deleteAll();
        Team team = new Team();
        team.setTeamName("折返跑接口班" + System.nanoTime());
        team.setMemberCount(10);
        teamId = teamRepository.save(team).getId();
    }

    private String body(int expected, int passed, Long version) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("testDate", day);
        map.put("expectedCount", expected);
        map.put("passedCount", passed);
        map.put("operator", "教员");
        if (version != null) {
            map.put("version", version);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void saveAndOverview_ratesMatch_everywhere() throws Exception {
        mockMvc.perform(put("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10, 5, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCount").value(10))
                .andExpect(jsonPath("$.passedCount").value(5))
                .andExpect(jsonPath("$.passRate").value(50.0));

        mockMvc.perform(get("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passRate").value(50.0));

        mockMvc.perform(get("/api/shuttle-run/summary").param("testDate", day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpected").value(10))
                .andExpect(jsonPath("$.totalPassed").value(5))
                .andExpect(jsonPath("$.passRate").value(50.0));

        mockMvc.perform(get("/api/shuttle-run/overview").param("testDate", day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId == " + teamId + ")].passRate").value(org.hamcrest.Matchers.hasItem(50.0)));

        // 改合格人数 5 -> 8：两处合格率都按新人数变成 80.0
        mockMvc.perform(put("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10, 8, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passRate").value(80.0));

        mockMvc.perform(get("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day))
                .andExpect(jsonPath("$.passRate").value(80.0));
        mockMvc.perform(get("/api/shuttle-run/summary").param("testDate", day))
                .andExpect(jsonPath("$.passRate").value(80.0));
    }

    @Test
    void save_passedGreaterThanExpected_returns400_andEverythingStaysAtOldValue() throws Exception {
        mockMvc.perform(put("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10, 8, null)))
                .andExpect(status().isOk());

        // 应测改小到 6、合格仍是 8：400，文案写明合格人数比应测还多
        mockMvc.perform(put("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(6, 8, 0L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("合格人数")));

        // 两处合格率仍是保存前的 80.0，人数仍是 10 / 8
        mockMvc.perform(get("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day))
                .andExpect(jsonPath("$.expectedCount").value(10))
                .andExpect(jsonPath("$.passedCount").value(8))
                .andExpect(jsonPath("$.passRate").value(80.0));
        mockMvc.perform(get("/api/shuttle-run/summary").param("testDate", day))
                .andExpect(jsonPath("$.totalExpected").value(10))
                .andExpect(jsonPath("$.totalPassed").value(8))
                .andExpect(jsonPath("$.passRate").value(80.0));
    }

    @Test
    void concurrentEdits_exactlyOne200_one409_winnerKept() throws Exception {
        mockMvc.perform(put("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day)
                .contentType(MediaType.APPLICATION_JSON).content(body(10, 5, null)))
                .andExpect(status().isOk());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        // 两个教员都基于 version=0 的同一版，分别要把合格人数改成 9 和 2
        Thread t1 = new Thread(new SaveTask(9, ready, start, ok, conflict));
        Thread t2 = new Thread(new SaveTask(2, ready, start, ok, conflict));
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        org.junit.jupiter.api.Assertions.assertEquals(1, ok.get());
        org.junit.jupiter.api.Assertions.assertEquals(1, conflict.get());

        // 先写成功的人数留下：行值、班组合格率、总览总合格率三处一致
        Integer winnerPassed = scoreRepository.findAll().get(0).getPassedCount();
        double expectedRate = winnerPassed == 9 ? 90.0 : 20.0;
        mockMvc.perform(get("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day))
                .andExpect(jsonPath("$.passedCount").value(winnerPassed))
                .andExpect(jsonPath("$.passRate").value(expectedRate));
        mockMvc.perform(get("/api/shuttle-run/summary").param("testDate", day))
                .andExpect(jsonPath("$.totalPassed").value(winnerPassed))
                .andExpect(jsonPath("$.passRate").value(expectedRate));
    }

    private class SaveTask implements Runnable {
        private final int passed;
        private final CountDownLatch ready;
        private final CountDownLatch start;
        private final AtomicInteger ok;
        private final AtomicInteger conflict;

        SaveTask(int passed, CountDownLatch ready, CountDownLatch start,
                 AtomicInteger ok, AtomicInteger conflict) {
            this.passed = passed;
            this.ready = ready;
            this.start = start;
            this.ok = ok;
            this.conflict = conflict;
        }

        @Override
        public void run() {
            try {
                ready.countDown();
                start.await();
                int status = mockMvc.perform(
                                put("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(10, passed, 0L)))
                        .andReturn().getResponse().getStatus();
                if (status == 200) {
                    ok.incrementAndGet();
                } else if (status == 409) {
                    conflict.incrementAndGet();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void missingRow_returns204_andUnscoredTeamHasNullsInOverview() throws Exception {
        mockMvc.perform(get("/api/shuttle-run/team/{teamId}/date/{day}", teamId, day))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/shuttle-run/overview").param("testDate", day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.teamId == " + teamId + ")].expectedCount")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }
}
