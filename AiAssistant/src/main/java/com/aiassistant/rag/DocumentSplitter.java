package com.aiassistant.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分器：按段落 + 长度切分 Markdown 文本（替代 langchain4j DocumentSplitters）。
 * 切分策略：先按双换行（段落）切分，再对超长段落按 maxSegmentSize 字符二次切分。
 */
@Component
public class DocumentSplitter {

    private static final int DEFAULT_MAX_SEGMENT_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    public List<Document> split(Document document) {
        return split(document, DEFAULT_MAX_SEGMENT_SIZE, DEFAULT_OVERLAP);
    }

    public List<Document> split(Document document, int maxSegmentSize, int overlap) {
        List<Document> segments = new ArrayList<>();
        if (document == null || document.getText() == null || document.getText().isEmpty()) {
            return segments;
        }
        String text = document.getText();
        String[] paragraphs = text.split("\\n\\s*\\n");

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= maxSegmentSize) {
                segments.add(newDocument(trimmed, document.getSource()));
            } else {
                for (int i = 0; i < trimmed.length(); i += (maxSegmentSize - overlap)) {
                    int end = Math.min(i + maxSegmentSize, trimmed.length());
                    String chunk = trimmed.substring(i, end).trim();
                    if (!chunk.isEmpty()) {
                        segments.add(newDocument(chunk, document.getSource()));
                    }
                    if (end >= trimmed.length()) {
                        break;
                    }
                }
            }
        }
        return segments;
    }

    private Document newDocument(String text, String source) {
        Document doc = new Document();
        doc.setText(text);
        doc.setSource(source);
        return doc;
    }
}
