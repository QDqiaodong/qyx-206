package com.example.app.repository;

import com.example.app.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByTeamName(String teamName);

    Page<Team> findByTeamNameContaining(String name, Pageable pageable);

    boolean existsByTeamName(String teamName);
}