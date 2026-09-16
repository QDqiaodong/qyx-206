package com.example.app.repository;

import com.example.app.entity.ShuttleRunScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShuttleRunScoreRepository extends JpaRepository<ShuttleRunScore, Long> {

    /** 同一班组同一测验日只允许一行：班组页/总览都按这个键取唯一一行。 */
    Optional<ShuttleRunScore> findByTeamIdAndTestDate(Long teamId, java.time.LocalDate testDate);

    /** 某测验日全部班组的成绩：总览当天合格率一次取齐。 */
    List<ShuttleRunScore> findByTestDate(java.time.LocalDate testDate);

    List<ShuttleRunScore> findByTeamIdOrderByTestDateDesc(Long teamId);

    /**
     * 按版本号整单更新：应测人数、合格人数在同一条 UPDATE 里一起落库。
     * 两个教员几乎同时改同一班组同一日时，先落库者把 version 顶到下一号，
     * 后落库者 WHERE version = :expectedVersion 匹配 0 行 ——
     * 他这一版整体作废，不会把先写成功的人数盖掉，也不会出现半新半旧。
     * 批量 UPDATE 不触发 @PreUpdate，update_time 在这里显式顶新。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ShuttleRunScore s SET s.expectedCount = :expectedCount, "
            + "s.passedCount = :passedCount, s.operator = :operator, "
            + "s.updateTime = CURRENT_TIMESTAMP, s.version = :expectedVersion + 1 "
            + "WHERE s.id = :id AND s.version = :expectedVersion")
    int updateVersioned(@Param("id") Long id,
                        @Param("expectedCount") Integer expectedCount,
                        @Param("passedCount") Integer passedCount,
                        @Param("operator") String operator,
                        @Param("expectedVersion") Long expectedVersion);
}
