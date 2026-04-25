package org.xhy.raglearn.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingConfig;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingModelFactory;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingProperties;
import org.xhy.raglearn.infrastructure.retrieval.JdbcManualTextChunkRepository;
import org.xhy.raglearn.infrastructure.retrieval.JdbcManualTextExperimentRepository;
import org.xhy.raglearn.infrastructure.retrieval.LangChain4jManualTextVectorGateway;
import org.testcontainers.containers.PostgreSQLContainer;
import dev.langchain4j.store.embedding.EmbeddingStore;

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

    @Autowired
    private ManualTextRetrievalAppService appService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("TRUNCATE TABLE manual_text_experiment RESTART IDENTITY CASCADE");
        jdbcTemplate.update("TRUNCATE TABLE public.vector_store");
    }

    @AfterAll
    void tearDown() {
        POSTGRES.stop();
    }

    @Test
    void compensates_vector_rows_when_a_later_vector_write_fails() {
        assertThatThrownBy(() -> appService.indexManualText(new ManualTextIndexCommand(
                "Intro Notes",
                "alpha beta\n\ngamma delta"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector store failed after first write");

        assertThat(countRows("manual_text_experiment")).isZero();
        assertThat(countRows("manual_text_chunk")).isZero();
        assertThat(countRows("public.vector_store")).isZero();
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
        EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
            EmbeddingProperties properties = new EmbeddingProperties();
            properties.setApiKey("test-key");
            properties.setDimension(1024);
            return new EmbeddingConfig().embeddingStore(dataSource, properties, "public.vector_store");
        }

        @Bean
        EmbeddingModelFactory embeddingModelFactory() {
            return new StubEmbeddingModelFactory();
        }

        @Bean
        LangChain4jManualTextVectorGateway realManualTextVectorGateway(
                EmbeddingStore<TextSegment> embeddingStore,
                EmbeddingModelFactory embeddingModelFactory,
                DataSource dataSource
        ) {
            return new LangChain4jManualTextVectorGateway(
                    embeddingStore,
                    embeddingModelFactory,
                    dataSource,
                    "public.vector_store"
            );
        }

        @Bean
        @Primary
        ManualTextVectorGateway manualTextVectorGateway(LangChain4jManualTextVectorGateway delegate) {
            return new ManualTextVectorGateway() {
                private boolean firstWriteDone;

                @Override
                public void storeChunk(ManualTextChunk chunk) {
                    if (!firstWriteDone) {
                        delegate.storeChunk(chunk);
                        firstWriteDone = true;
                        return;
                    }
                    throw new IllegalStateException("vector store failed after first write");
                }

                @Override
                public java.util.List<ChunkSearchMatch> search(long experimentId, String question, int topK) {
                    return delegate.search(experimentId, question, topK);
                }

                @Override
                public void deleteByExperimentId(long experimentId) {
                    delegate.deleteByExperimentId(experimentId);
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

    private static final class StubEmbeddingModelFactory extends EmbeddingModelFactory {

        private final OpenAiEmbeddingModel model = new StubOpenAiEmbeddingModel();

        private StubEmbeddingModelFactory() {
            super(testProperties());
        }

        @Override
        public OpenAiEmbeddingModel create() {
            return model;
        }

        private static EmbeddingProperties testProperties() {
            EmbeddingProperties properties = new EmbeddingProperties();
            properties.setApiKey("test-key");
            properties.setBaseUrl("http://localhost");
            properties.setModel("test-model");
            properties.setDimension(1024);
            return properties;
        }
    }

    private static final class StubOpenAiEmbeddingModel extends OpenAiEmbeddingModel {

        private StubOpenAiEmbeddingModel() {
            super(OpenAiEmbeddingModel.builder()
                    .apiKey("test-key")
                    .baseUrl("http://localhost")
                    .modelName("test-model")
                    .dimensions(1024));
        }

        @Override
        public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(vectorFor(segment.text())))
                    .toList());
        }

        private static float[] vectorFor(String text) {
            float[] vector = new float[1024];
            String normalized = text.toLowerCase();
            if (normalized.contains("alpha")) {
                vector[0] = 1.0f;
                return vector;
            }
            vector[1] = 1.0f;
            return vector;
        }
    }
}
