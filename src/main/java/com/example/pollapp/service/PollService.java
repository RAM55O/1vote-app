package com.example.pollapp.service;

import com.example.pollapp.dto.CreatePollRequest;
import com.example.pollapp.dto.OptionDto;
import com.example.pollapp.dto.PollDto;
import com.example.pollapp.dto.UserVoteStatusDto;
import com.example.pollapp.model.Option;
import com.example.pollapp.model.Poll;
import com.example.pollapp.model.VoteRecord;
import com.example.pollapp.repository.OptionRepository;
import com.example.pollapp.repository.PollRepository;
import com.example.pollapp.repository.VoteRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PollService {

    private static final Logger log = LoggerFactory.getLogger(PollService.class);

    private final PollRepository pollRepository;
    private final OptionRepository optionRepository;
    private final VoteRecordRepository voteRecordRepository;

    // Thread-safe map of active SSE emitters grouped by Poll ID
    private final Map<Long, List<SseEmitter>> pollEmitters = new ConcurrentHashMap<>();

    public PollService(PollRepository pollRepository,
                       OptionRepository optionRepository,
                       VoteRecordRepository voteRecordRepository) {
        this.pollRepository = pollRepository;
        this.optionRepository = optionRepository;
        this.voteRecordRepository = voteRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<PollDto> getAllPolls() {
        List<Poll> polls = pollRepository.findAllWithActiveOptions();
        List<PollDto> dtos = new ArrayList<>();
        for (Poll poll : polls) {
            dtos.add(convertToDto(poll));
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public PollDto getPollById(Long id) {
        Poll poll = pollRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found with id: " + id));
        return convertToDto(poll);
    }

    @Transactional(readOnly = true)
    public UserVoteStatusDto getUserVoteStatus(Long pollId, String voterId) {
        if (voterId == null || voterId.trim().isEmpty()) {
            return new UserVoteStatusDto(false, null);
        }
        Optional<VoteRecord> record = voteRecordRepository.findByPollIdAndVoterId(pollId, voterId.trim());
        return record.map(r -> new UserVoteStatusDto(true, r.getOptionId()))
                     .orElseGet(() -> new UserVoteStatusDto(false, null));
    }

    @Transactional
    public PollDto createPoll(CreatePollRequest request) {
        Poll poll = new Poll(request.getQuestion().trim());
        for (String optText : request.getOptions()) {
            if (optText != null && !optText.trim().isEmpty()) {
                Option option = new Option(optText.trim());
                poll.addOption(option);
            }
        }

        if (poll.getOptions().size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A poll must have at least 2 non-empty options");
        }

        Poll saved = pollRepository.save(poll);
        return convertToDto(saved);
    }

    /**
     * Executes atomic vote increment with strict single-vote enforcement.
     * Prevents duplicate votes by recording a unique voter token & IP per poll.
     */
    @Transactional
    public PollDto castVote(Long pollId, Long optionId, String voterId, String ipAddress) {
        // Verify poll exists first
        if (!pollRepository.existsById(pollId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found with id: " + pollId);
        }

        if (voterId == null || voterId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voter ID is required");
        }

        String sanitizedVoterId = voterId.trim();

        // Check if voter has already voted in this poll
        if (voteRecordRepository.existsByPollIdAndVoterId(pollId, sanitizedVoterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already voted in this poll.");
        }

        // Record the vote uniquely to guard against concurrent submissions
        try {
            VoteRecord voteRecord = new VoteRecord(pollId, optionId, sanitizedVoterId, ipAddress);
            voteRecordRepository.saveAndFlush(voteRecord);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate vote attempt caught by DB constraint for poll {} and voter {}", pollId, sanitizedVoterId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already voted in this poll.");
        }

        // Execute atomic DB increment
        int rowsUpdated = optionRepository.incrementVoteCount(optionId, pollId);
        if (rowsUpdated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid option ID " + optionId + " for poll ID " + pollId);
        }

        // Fetch fresh state and convert to DTO
        Poll updatedPoll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found"));

        PollDto dto = convertToDto(updatedPoll);

        // Broadcast real-time update to all connected SSE clients
        broadcastUpdate(pollId, dto);

        return dto;
    }

    /**
     * Backwards-compatible convenience method.
     */
    @Transactional
    public PollDto castVote(Long pollId, Long optionId) {
        return castVote(pollId, optionId, UUID.randomUUID().toString(), "127.0.0.1");
    }

    /**
     * Subscribes a client to real-time Server-Sent Events (SSE) for a specific poll.
     */
    public SseEmitter subscribeToPoll(Long pollId) {
        // Verify poll exists
        if (!pollRepository.existsById(pollId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found with id: " + pollId);
        }

        // 30 minute timeout for SSE stream
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        List<SseEmitter> emitters = pollEmitters.computeIfAbsent(pollId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));

        // Send initial state immediately
        try {
            PollDto current = getPollById(pollId);
            emitter.send(SseEmitter.event().name("poll-update").data(current));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    private void broadcastUpdate(Long pollId, PollDto dto) {
        List<SseEmitter> emitters = pollEmitters.get(pollId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("poll-update").data(dto));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    public PollDto convertToDto(Poll poll) {
        long totalVotes = 0L;
        if (poll.getOptions() != null) {
            for (Option opt : poll.getOptions()) {
                totalVotes += (opt.getVoteCount() != null ? opt.getVoteCount() : 0L);
            }
        }

        List<OptionDto> optionDtos = new ArrayList<>();
        if (poll.getOptions() != null) {
            for (Option opt : poll.getOptions()) {
                long votes = opt.getVoteCount() != null ? opt.getVoteCount() : 0L;
                double percentage = 0.0;
                if (totalVotes > 0) {
                    percentage = BigDecimal.valueOf((double) votes / totalVotes * 100.0)
                            .setScale(1, RoundingMode.HALF_UP)
                            .doubleValue();
                }
                optionDtos.add(new OptionDto(opt.getId(), opt.getText(), votes, percentage));
            }
        }

        return new PollDto(poll.getId(), poll.getQuestion(), poll.getCreatedAt(), totalVotes, optionDtos);
    }
}
