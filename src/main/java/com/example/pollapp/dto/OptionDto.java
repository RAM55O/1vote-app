package com.example.pollapp.dto;

public class OptionDto {
    private Long id;
    private String text;
    private Long voteCount;
    private Double percentage;

    public OptionDto() {
    }

    public OptionDto(Long id, String text, Long voteCount, Double percentage) {
        this.id = id;
        this.text = text;
        this.voteCount = voteCount;
        this.percentage = percentage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }
}
