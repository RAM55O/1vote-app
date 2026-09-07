package com.example.pollapp.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PollDto {
    private Long id;
    private String question;
    private LocalDateTime createdAt;
    private Long totalVotes;
    private List<OptionDto> options = new ArrayList<>();

    public PollDto() {
    }

    public PollDto(Long id, String question, LocalDateTime createdAt, Long totalVotes, List<OptionDto> options) {
        this.id = id;
        this.question = question;
        this.createdAt = createdAt;
        this.totalVotes = totalVotes;
        this.options = options;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(Long totalVotes) {
        this.totalVotes = totalVotes;
    }

    public List<OptionDto> getOptions() {
        return options;
    }

    public void setOptions(List<OptionDto> options) {
        this.options = options;
    }
}
