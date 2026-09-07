package com.example.pollapp.repository;

import com.example.pollapp.model.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface OptionRepository extends JpaRepository<Option, Long> {

    List<Option> findByPollIdOrderByIdAsc(Long pollId);

    /**
     * Atomically increments the vote_count for a given option belonging to a poll.
     * Database-level atomic UPDATE ensures thread safety and prevents lost updates under concurrent voting.
     *
     * @param optionId The ID of the option to vote for.
     * @param pollId   The ID of the parent poll.
     * @return Number of rows updated (1 if successful, 0 if option not found or poll mismatch).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Option o SET o.voteCount = o.voteCount + 1 WHERE o.id = :optionId AND o.poll.id = :pollId")
    int incrementVoteCount(@Param("optionId") Long optionId, @Param("pollId") Long pollId);
}
