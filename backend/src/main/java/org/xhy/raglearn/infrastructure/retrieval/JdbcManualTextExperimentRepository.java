package org.xhy.raglearn.infrastructure.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
public class JdbcManualTextExperimentRepository implements ManualTextExperimentRepository {

    private static final RowMapper<ManualTextExperiment> ROW_MAPPER = (rs, rowNum) -> new ManualTextExperiment(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("raw_text"),
            rs.getInt("chunk_count")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcManualTextExperimentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ManualTextExperiment create(String title, String rawText) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO manual_text_experiment (title, raw_text, chunk_count)
                    VALUES (?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setString(2, rawText);
            statement.setInt(3, 0);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return new ManualTextExperiment(key.longValue(), title, rawText, 0);
    }

    @Override
    public void updateChunkCount(long experimentId, int chunkCount) {
        jdbcTemplate.update(
                """
                UPDATE manual_text_experiment
                SET chunk_count = ?
                WHERE id = ?
                """,
                chunkCount,
                experimentId
        );
    }

    @Override
    public ManualTextExperiment findById(long experimentId) {
        return jdbcTemplate.query(
                """
                SELECT id, title, raw_text, chunk_count
                FROM manual_text_experiment
                WHERE id = ?
                """,
                ROW_MAPPER,
                experimentId
        ).stream().findFirst().orElse(null);
    }
}
