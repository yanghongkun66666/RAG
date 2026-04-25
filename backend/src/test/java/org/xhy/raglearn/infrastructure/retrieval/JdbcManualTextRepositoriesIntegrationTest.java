package org.xhy.raglearn.infrastructure.retrieval;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcManualTextRepositoriesIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static JdbcTemplate jdbcTemplate;
    private static ManualTextExperimentRepository experimentRepository;
    private static ManualTextChunkRepository chunkRepository;

    @BeforeAll
    static void setUp() {
        POSTGRES.start();

        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        experimentRepository = new JdbcManualTextExperimentRepository(jdbcTemplate);
        chunkRepository = new JdbcManualTextChunkRepository(jdbcTemplate);
    }

    @AfterAll
    static void tearDown() {
        POSTGRES.stop();
    }

    @Test
    void migratesAndPersistsManualTextExperimentsAndChunks() {
        Integer experimentTable = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_name = 'manual_text_experiment'
                """,
                Integer.class
        );
        Integer chunkTable = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_name = 'manual_text_chunk'
                """,
                Integer.class
        );

        assertEquals(1, experimentTable);
        assertEquals(1, chunkTable);

        ManualTextExperiment experiment = experimentRepository.create("Intro Notes", "alpha beta\n\ngamma delta");
        experimentRepository.updateChunkCount(experiment.id(), 2);

        Optional<ManualTextExperiment> reloadedExperiment = experimentRepository.findById(experiment.id());
        assertTrue(reloadedExperiment.isPresent());
        assertEquals(new ManualTextExperiment(experiment.id(), "Intro Notes", "alpha beta\n\ngamma delta", 2), reloadedExperiment.get());

        List<ManualTextChunk> savedChunks = chunkRepository.saveAll(experiment.id(), List.of(
                new ManualTextChunkDraft(0, "alpha beta"),
                new ManualTextChunkDraft(1, "gamma delta")
        ));
        List<ManualTextChunk> reloadedChunks = chunkRepository.findByExperimentId(experiment.id());

        assertEquals(2, savedChunks.size());
        assertEquals(savedChunks, reloadedChunks);

        jdbcTemplate.update("DELETE FROM manual_text_experiment WHERE id = ?", experiment.id());

        Integer orphanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manual_text_chunk WHERE experiment_id = ?",
                Integer.class,
                experiment.id()
        );
        assertEquals(0, orphanCount);
    }

    @Test
    void enforcesUniqueChunkIndexPerExperiment() {
        ManualTextExperiment experiment = experimentRepository.create("Intro Notes", "alpha beta");

        chunkRepository.saveAll(experiment.id(), List.of(
                new ManualTextChunkDraft(0, "alpha beta")
        ));

        assertThrows(DataIntegrityViolationException.class, () -> chunkRepository.saveAll(experiment.id(), List.of(
                new ManualTextChunkDraft(0, "duplicate alpha beta")
        )));
    }
}
