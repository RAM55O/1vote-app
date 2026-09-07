package com.example.pollapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreatePollRequest {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 500, message = "Question cannot exceed 500 characters")
    private String question;

    @NotEmpty(message = "At least two options are required")
    @Size(min = 2, max = 10, message = "Poll must have between 2 and 10 options")
    private List<@NotBlank(message = "Option text cannot be blank") String> options;

    public CreatePollRequest() {
    }

    public CreatePollRequest(String question, List<String> options) {
        this.question = question;
        this.options = options;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
