package org.xhy.raglearn.domain.retrieval.service;

import org.junit.jupiter.api.Test;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleTextChunkerTest {

    @Test
    void splitsOnBlankLinesBeforeFallingBackToWindows() {
        SimpleTextChunker chunker = new SimpleTextChunker(12, 4);
        List<ManualTextChunkDraft> chunks = chunker.chunk("alpha beta\n\ngamma delta");

        assertEquals(List.of(
                new ManualTextChunkDraft(0, "alpha beta"),
                new ManualTextChunkDraft(1, "gamma delta")
        ), chunks);
    }

    @Test
    void createsOverlappingWindowsForLongParagraphs() {
        SimpleTextChunker chunker = new SimpleTextChunker(5, 2);
        List<ManualTextChunkDraft> chunks = chunker.chunk("abcdefghij");

        assertEquals(List.of(
                new ManualTextChunkDraft(0, "abcde"),
                new ManualTextChunkDraft(1, "defgh"),
                new ManualTextChunkDraft(2, "ghij")
        ), chunks);
    }

    @Test
    void returnsEmptyListForBlankInput() {
        SimpleTextChunker chunker = new SimpleTextChunker(12, 4);
        assertEquals(List.of(), chunker.chunk(" \r\n\t "));
    }
}
