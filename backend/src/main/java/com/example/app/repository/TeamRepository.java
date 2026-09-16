package com.example.app.repository;

import com.example.app.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByTeamName(String teamName);

    Page<Team> findByTeamNameContaining(String name, Pageable pageable);

    boolean existsByTeamName(String teamName);

    /**
     * 按版本号整单更新：编制、说明、名称在同一条 UPDATE 里一起落库。
     * 两人同时改同一个班时，先落库者把 version 顶到下一号，
     * 后落库者 WHERE version = :expectedVersion 匹配 0 行 ——
     * 他那一版（哪怕只改了一个字段）整体作废，不会出现半新半旧。
     * 批量 UPDATE 不触发 @PreUpdate，update_time 在这里显式顶新。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Team t SET t.teamName = :teamName, "
            + "t.memberCount = :memberCount, t.description = :description, "
            + "t.updateTime = CURRENT_TIMESTAMP, t.version = :expectedVersion + 1 "
            + "WHERE t.id = :id AND t.version = :expectedVersion")
    int updateVersioned(@Param("id") Long id,
                        @Param("teamName") String teamName,
                        @Param("memberCount") Integer memberCount,
                        @Param("description") String description,
                        @Param("expectedVersion") Long expectedVersion);
}