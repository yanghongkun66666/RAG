package org.xhy.raglearn.domain.retrieval.service;

import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;

import java.util.ArrayList;
import java.util.List;

public class SimpleTextChunker {

    private final int chunkSize;
    private final int overlap;

    public SimpleTextChunker() {
        this(500, 100);
    }

    public SimpleTextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must be greater than or equal to 0");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<ManualTextChunkDraft> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<ManualTextChunkDraft> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (String paragraph : normalized.split("(?:\\n\\s*\\n)+")) {
            String content = normalizeContent(paragraph);
            if (content.isEmpty()) {
                continue;
            }

            if (content.length() <= chunkSize) {
                chunks.add(new ManualTextChunkDraft(chunkIndex++, content));
                continue;
            }

            int step = Math.max(1, chunkSize - overlap);
            for (int start = 0; start < content.length(); start += step) {
                int end = Math.min(content.length(), start + chunkSize);
                String window = normalizeContent(content.substring(start, end));
                if (!window.isBlank()) {
                    chunks.add(new ManualTextChunkDraft(chunkIndex++, window));
                }
                if (end == content.length()) {
                    break;
                }
            }
        }

        return List.copyOf(chunks);
    }

    private String normalizeContent(String content) {
        return content.trim();
    }
}
