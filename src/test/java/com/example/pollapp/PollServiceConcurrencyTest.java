package com.example.pollapp;

import com.example.pollapp.dto.CreatePollRequest;
import com.example.pollapp.dto.PollDto;
import com.example.pollapp.service.PollService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PollServiceConcurrencyTest {

    @Autowired
    private PollService pollService;

    @Test
    void testAtomicConcurrentVotingWithUniqueUsers() throws InterruptedException {
        // Create a new fresh poll with 2 options
        CreatePollRequest request = new CreatePollRequest(
                "High Concurrency Stress Test",
                List.of("Option Alpha", "Option Beta")
        );
        PollDto poll = pollService.createPoll(request);
        Long pollId = poll.getId();
        Long optAId = poll.getOptions().get(0).getId();
        Long optBId = poll.getOptions().get(1).getId();

        int numberOfVotesPerOption = 50; // Total 100 votes
        int totalThreads = numberOfVotesPerOption * 2;

        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successfulVotes = new AtomicInteger(0);

        // Submit 50 votes for Option Alpha (each with unique voterId)
        for (int i = 0; i < numberOfVotesPerOption; i++) {
            final String voterId = "user-alpha-" + UUID.randomUUID();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    pollService.castVote(pollId, optAId, voterId, "192.168.1.10");
                    successfulVotes.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Submit 50 votes for Option Beta (each with unique voterId)
        for (int i = 0; i < numberOfVotesPerOption; i++) {
            final String voterId = "user-beta-" + UUID.randomUUID();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    pollService.castVote(pollId, optBId, voterId, "192.168.1.20");
                    successfulVotes.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean allReady = readyLatch.await(5, TimeUnit.SECONDS);
        assertTrue(allReady, "All threads should be ready");

        startLatch.countDown(); // Fire all 100 threads at the same time

        boolean allFinished = doneLatch.await(15, TimeUnit.SECONDS);
        assertTrue(allFinished, "All threads should complete within timeout");

        executor.shutdown();

        // Verify that all 100 votes succeeded
        assertEquals(totalThreads, successfulVotes.get(), "All 100 votes must succeed");

        // Verify DB tally counts
        PollDto finalPoll = pollService.getPollById(pollId);
        assertEquals(100L, finalPoll.getTotalVotes(), "Total votes must be exactly 100");
        assertEquals(50L, finalPoll.getOptions().get(0).getVoteCount(), "Option Alpha must have exactly 50 votes");
        assertEquals(50L, finalPoll.getOptions().get(1).getVoteCount(), "Option Beta must have exactly 50 votes");
        assertEquals(50.0, finalPoll.getOptions().get(0).getPercentage());
        assertEquals(50.0, finalPoll.getOptions().get(1).getPercentage());
    }

    @Test
    void testConcurrentDuplicateVotesFromSameUserBlocked() throws InterruptedException {
        // Create poll
        CreatePollRequest request = new CreatePollRequest(
                "Duplicate Vote Test",
                List.of("Choice A", "Choice B")
        );
        PollDto poll = pollService.createPoll(request);
        Long pollId = poll.getId();
        Long optAId = poll.getOptions().get(0).getId();

        String singleVoterId = "single-user-" + UUID.randomUUID();
        int attemptThreads = 20;

        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch readyLatch = new CountDownLatch(attemptThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(attemptThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < attemptThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    pollService.castVote(pollId, optAId, singleVoterId, "10.0.0.1");
                    successCount.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 vote must succeed, and 19 must be rejected with 409 Conflict
        assertEquals(1, successCount.get(), "Exactly 1 concurrent vote must succeed");
        assertEquals(attemptThreads - 1, conflictCount.get(), "All other concurrent votes must be rejected as 409 Conflict");

        PollDto finalPoll = pollService.getPollById(pollId);
        assertEquals(1L, finalPoll.getTotalVotes(), "Total votes in database must be exactly 1");
    }
}
