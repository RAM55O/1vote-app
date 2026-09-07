package com.example.pollapp;

import com.example.pollapp.dto.CreatePollRequest;
import com.example.pollapp.dto.VoteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PollApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void testGetAllPolls() throws Exception {
        mockMvc.perform(get("/api/polls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].question", notNullValue()))
                .andExpect(jsonPath("$[0].options", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void testCreatePollVoteAndPreventDuplicate() throws Exception {
        // 1. Create a new poll
        CreatePollRequest createReq = new CreatePollRequest(
                "Which architecture is best for microservices?",
                List.of("Event Driven", "REST / gRPC", "Monolith First")
        );

        String responseJson = mockMvc.perform(post("/api/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question", is("Which architecture is best for microservices?")))
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.totalVotes", is(0)))
                .andReturn().getResponse().getContentAsString();

        Long pollId = objectMapper.readTree(responseJson).get("id").asLong();
        Long firstOptionId = objectMapper.readTree(responseJson).get("options").get(0).get("id").asLong();
        Long secondOptionId = objectMapper.readTree(responseJson).get("options").get(1).get("id").asLong();
        String voterId = "test-voter-" + UUID.randomUUID();

        // 2. Cast a vote
        VoteRequest voteReq = new VoteRequest(firstOptionId, voterId);
        mockMvc.perform(post("/api/polls/" + pollId + "/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVotes", is(1)))
                .andExpect(jsonPath("$.options[0].voteCount", is(1)))
                .andExpect(jsonPath("$.options[0].percentage", is(100.0)));

        // 3. Duplicate vote from same voter should be rejected with 409 Conflict
        VoteRequest duplicateReq = new VoteRequest(secondOptionId, voterId);
        mockMvc.perform(post("/api/polls/" + pollId + "/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isConflict());

        // 4. Verify vote status endpoint
        mockMvc.perform(get("/api/polls/" + pollId + "/vote-status")
                        .param("voterId", voterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasVoted", is(true)))
                .andExpect(jsonPath("$.optionId", is(firstOptionId.intValue())));
    }
}
