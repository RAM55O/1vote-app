package com.example.pollapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VoteRequest {

    @NotNull(message = "Option ID must not be null")
    private Long optionId;

    @NotBlank(message = "Voter ID must not be blank")
    private String voterId;

    public VoteRequest() {
    }

    public VoteRequest(Long optionId) {
        this.optionId = optionId;
    }

    public VoteRequest(Long optionId, String voterId) {
        this.optionId = optionId;
        this.voterId = voterId;
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
}
