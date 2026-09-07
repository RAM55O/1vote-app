package com.example.pollapp.config;

import com.example.pollapp.model.Option;
import com.example.pollapp.model.Poll;
import com.example.pollapp.repository.PollRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initSamplePolls(PollRepository pollRepository) {
        return args -> {
            if (pollRepository.count() == 0) {
                log.info("Seeding initial sample polls and options...");

                // Poll 1: Technology & Frameworks
                Poll techPoll = new Poll("What is your primary backend framework for high-concurrency systems in 2026?");
                techPoll.addOption(new Option("Spring Boot (Java)"));
                techPoll.addOption(new Option("Go (Gin / Fiber)"));
                techPoll.addOption(new Option("Node.js (NestJS / Express)"));
                techPoll.addOption(new Option("Python (FastAPI)"));
                techPoll.addOption(new Option("Rust (Actix / Axum)"));

                // Seed some initial votes
                techPoll.getOptions().get(0).setVoteCount(42L);
                techPoll.getOptions().get(1).setVoteCount(28L);
                techPoll.getOptions().get(2).setVoteCount(19L);
                techPoll.getOptions().get(3).setVoteCount(24L);
                techPoll.getOptions().get(4).setVoteCount(15L);

                pollRepository.save(techPoll);

                // Poll 2: Database architecture
                Poll dbPoll = new Poll("Which database strategy do you prefer for scalable cloud microservices?");
                dbPoll.addOption(new Option("PostgreSQL with Read Replicas"));
                dbPoll.addOption(new Option("Distributed SQL (CockroachDB / Yugabyte)"));
                dbPoll.addOption(new Option("NoSQL (MongoDB / DynamoDB)"));
                dbPoll.addOption(new Option("In-Memory Hybrid (Redis + SQLite/Postgres)"));

                dbPoll.getOptions().get(0).setVoteCount(55L);
                dbPoll.getOptions().get(1).setVoteCount(21L);
                dbPoll.getOptions().get(2).setVoteCount(18L);
                dbPoll.getOptions().get(3).setVoteCount(33L);

                pollRepository.save(dbPoll);

                log.info("Initialized {} sample polls successfully.", pollRepository.count());
            }
        };
    }
}
