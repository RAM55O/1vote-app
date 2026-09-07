package com.example.pollapp.repository;

import com.example.pollapp.model.VoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteRecordRepository extends JpaRepository<VoteRecord, Long> {

    boolean existsByPollIdAndVoterId(Long pollId, String voterId);

    Optional<VoteRecord> findByPollIdAndVoterId(Long pollId, String voterId);
}
