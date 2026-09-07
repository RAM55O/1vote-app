package com.example.pollapp.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "vote_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_poll_voter", columnNames = {"poll_id", "voter_id"})
    },
    indexes = {
        @Index(name = "idx_poll_voter", columnList = "poll_id, voter_id")
    }
)
public class VoteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "voter_id", nullable = false, length = 128)
    private String voterId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt = Instant.now();

    public VoteRecord() {
    }

    public VoteRecord(Long pollId, Long optionId, String voterId, String ipAddress) {
        this.pollId = pollId;
        this.optionId = optionId;
        this.voterId = voterId;
        this.ipAddress = ipAddress;
        this.votedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPollId() {
        return pollId;
    }

    public void setPollId(Long pollId) {
        this.pollId = pollId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public String getVoterId() {
        return voterId;
    }

    public void setVoterId(String voterId) {
        this.voterId = voterId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getVotedAt() {
        return votedAt;
    }

    public void setVotedAt(Instant votedAt) {
        this.votedAt = votedAt;
    }
}
