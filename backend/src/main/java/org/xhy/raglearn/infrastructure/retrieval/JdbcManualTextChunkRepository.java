package org.xhy.raglearn.infrastructure.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcManualTextChunkRepository implements ManualTextChunkRepository {

    private static final RowMapper<ManualTextChunk> ROW_MAPPER = (rs, rowNum) -> new ManualTextChunk(
            rs.getLong("id"),
            rs.getLong("experiment_id"),
            rs.getInt("chunk_index"),
            rs.getString("content")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcManualTextChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ManualTextChunk> saveAll(long experimentId, List<ManualTextChunkDraft> drafts) {
        List<ManualTextChunk> chunks = new ArrayList<>();

        for (ManualTextChunkDraft draft : drafts) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO manual_text_chunk (experiment_id, chunk_index, content)
                        VALUES (?, ?, ?)
                        """,
                        new String[]{"id"}
                );
                statement.setLong(1, experimentId);
                statement.setInt(2, draft.chunkIndex());
                statement.setString(3, draft.content());
                return statement;
            }, keyHolder);

            Number key = keyHolder.getKey();
            chunks.add(new ManualTextChunk(key.longValue(), experimentId, draft.chunkIndex(), draft.content()));
        }

        return List.copyOf(chunks);
    }

    @Override
    public List<ManualTextChunk> findByExperimentId(long experimentId) {
        return jdbcTemplate.query(
                """
                SELECT id, experiment_id, chunk_index, content
                FROM manual_text_chunk
                WHERE experiment_id = ?
                ORDER BY chunk_index ASC
                """,
                ROW_MAPPER,
                experimentId
        );
    }
}
