package org.xhy.raglearn.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;
import org.xhy.raglearn.infrastructure.retrieval.JdbcManualTextChunkRepository;
import org.xhy.raglearn.infrastructure.retrieval.JdbcManualTextExperimentRepository;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ManualTextRetrievalAppServiceTransactionIntegrationTest.TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManualTextRetrievalAppServiceTransactionIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static {
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
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ManualTextRetrievalAppService appService;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("TRUNCATE TABLE manual_text_experiment RESTART IDENTITY CASCADE");
    }

    @AfterAll
    void tearDown() {
        POSTGRES.stop();
    }

    @Test
    void rolls_back_business_rows_when_vector_storage_fails() {
        assertThatThrownBy(() -> appService.indexManualText(new ManualTextIndexCommand(
                "Intro Notes",
                "alpha beta\n\ngamma delta"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector store failed");

        assertThat(countRows("manual_text_experiment")).isZero();
        assertThat(countRows("manual_text_chunk")).isZero();
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword()
            );
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ManualTextExperimentRepository manualTextExperimentRepository(JdbcTemplate jdbcTemplate) {
            return new JdbcManualTextExperimentRepository(jdbcTemplate);
        }

        @Bean
        ManualTextChunkRepository manualTextChunkRepository(JdbcTemplate jdbcTemplate) {
            return new JdbcManualTextChunkRepository(jdbcTemplate);
        }

        @Bean
        ManualTextVectorGateway manualTextVectorGateway() {
            return new ManualTextVectorGateway() {
                @Override
                public void storeChunk(ManualTextChunk chunk) {
                    throw new IllegalStateException("vector store failed");
                }

                @Override
                public java.util.List<ChunkSearchMatch> search(long experimentId, String question, int topK) {
                    return java.util.List.of();
                }
            };
        }

        @Bean
        SimpleTextChunker simpleTextChunker() {
            return new SimpleTextChunker(200, 20);
        }

        @Bean
        ManualTextRetrievalAppService manualTextRetrievalAppService(
                ManualTextExperimentRepository experimentRepository,
                ManualTextChunkRepository chunkRepository,
                ManualTextVectorGateway vectorGateway,
                SimpleTextChunker chunker
        ) {
            return new ManualTextRetrievalAppService(
                    experimentRepository,
                    chunkRepository,
                    vectorGateway,
                    chunker
            );
        }
    }
}
