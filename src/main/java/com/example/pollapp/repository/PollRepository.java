package com.example.pollapp.repository;

import com.example.pollapp.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {

    @Query("SELECT p FROM Poll p LEFT JOIN FETCH p.options ORDER BY p.createdAt DESC")
    List<Poll> findAllWithActiveOptions();

    @Query("SELECT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.id = :id")
    Optional<Poll> findByIdWithOptions(Long id);
}
