package com.example.pollapp.controller;

import com.example.pollapp.dto.CreatePollRequest;
import com.example.pollapp.dto.PollDto;
import com.example.pollapp.dto.UserVoteStatusDto;
import com.example.pollapp.dto.VoteRequest;
import com.example.pollapp.service.PollService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/polls")
@CrossOrigin(origins = "*")
public class PollController {

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    @GetMapping
    public ResponseEntity<List<PollDto>> getAllPolls() {
        return ResponseEntity.ok(pollService.getAllPolls());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PollDto> getPollById(@PathVariable Long id) {
        return ResponseEntity.ok(pollService.getPollById(id));
    }

    @GetMapping("/{pollId}/vote-status")
    public ResponseEntity<UserVoteStatusDto> getVoteStatus(
            @PathVariable Long pollId,
            @RequestParam(required = false) String voterId) {
        return ResponseEntity.ok(pollService.getUserVoteStatus(pollId, voterId));
    }

    @PostMapping
    public ResponseEntity<PollDto> createPoll(@Valid @RequestBody CreatePollRequest request) {
        PollDto created = pollService.createPoll(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Atomically votes on an option within a poll, strictly checking single-vote per voter.
     */
    @PostMapping("/{pollId}/vote")
    public ResponseEntity<PollDto> vote(
            @PathVariable Long pollId,
            @Valid @RequestBody VoteRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);
        PollDto updated = pollService.castVote(pollId, request.getOptionId(), request.getVoterId(), clientIp);
        return ResponseEntity.ok(updated);
    }

    /**
     * Server-Sent Events (SSE) endpoint for real-time live poll updates.
     */
    @GetMapping(value = "/{pollId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPoll(@PathVariable Long pollId) {
        return pollService.subscribeToPoll(pollId);
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
