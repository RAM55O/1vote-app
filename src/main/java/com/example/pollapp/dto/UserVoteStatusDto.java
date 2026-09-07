package com.example.pollapp.dto;

public class UserVoteStatusDto {

    private boolean hasVoted;
    private Long optionId;

    public UserVoteStatusDto() {
    }

    public UserVoteStatusDto(boolean hasVoted, Long optionId) {
        this.hasVoted = hasVoted;
        this.optionId = optionId;
    }

    public boolean isHasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }
}
