package com.example.pollapp.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "options")
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    @JsonBackReference
    private Poll poll;

    @Column(nullable = false, length = 255)
    private String text;

    @Column(name = "vote_count", nullable = false)
    private Long voteCount = 0L;

    public Option() {
    }

    public Option(String text) {
        this.text = text;
        this.voteCount = 0L;
    }

    public Option(String text, Poll poll) {
        this.text = text;
        this.poll = poll;
        this.voteCount = 0L;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Poll getPoll() {
        return poll;
    }

    public void setPoll(Poll poll) {
        this.poll = poll;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Long getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(Long voteCount) {
        this.voteCount = voteCount;
    }
}
