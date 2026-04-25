package org.xhy.raglearn.infrastructure.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingConfig;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingModelFactory;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingProperties;

class LangChain4jManualTextVectorGatewayIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static JdbcTemplate jdbcTemplate;

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
    }

    @AfterAll
    static void tearDown() {
        POSTGRES.stop();
    }

    @Test
    void flyway_creates_vector_store_table_before_embedding_store_initialization() {
        Integer vectorStoreTable = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'vector_store'
                """,
                Integer.class
        );

        assertThat(vectorStoreTable).isEqualTo(1);
    }

    @Test
    void creates_real_embedding_store_and_round_trips_gateway_search() {
        Integer vectorStoreTable = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'vector_store'
                """,
                Integer.class
        );

        assertThat(vectorStoreTable).isEqualTo(1);
        jdbcTemplate.update("TRUNCATE TABLE public.vector_store");

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setApiKey("test-key");
        properties.setDimension(1024);

        EmbeddingStore<TextSegment> embeddingStore =
                new EmbeddingConfig().embeddingStore(dataSource(), properties, "public.vector_store");
        LangChain4jManualTextVectorGateway gateway =
                new LangChain4jManualTextVectorGateway(
                        embeddingStore,
                        new StubEmbeddingModelFactory(),
                        dataSource(),
                        "public.vector_store"
                );

        gateway.storeChunk(new ManualTextChunk(101L, 1L, 0, "pgvector stores embeddings"));
        gateway.storeChunk(new ManualTextChunk(202L, 2L, 0, "pgvector stores embeddings"));

        assertThat(gateway.search(1L, "pgvector", 5))
                .hasSize(1)
                .first()
                .satisfies(match -> {
                    assertThat(match.chunkId()).isEqualTo(101L);
                    assertThat(match.chunkIndex()).isEqualTo(0);
                    assertThat(match.content()).isEqualTo("pgvector stores embeddings");
                });
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
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
                    .dimensions(4));
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(vectorFor(segment.text())))
                    .toList());
        }

        private static float[] vectorFor(String text) {
            float[] vector = new float[1024];
            String normalized = text.toLowerCase();
            if (normalized.contains("pgvector")) {
                vector[0] = 1.0f;
                return vector;
            }
            if (normalized.contains("spring")) {
                vector[1] = 1.0f;
                return vector;
            }
            vector[2] = 1.0f;
            return vector;
        }
    }
}
