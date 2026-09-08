package com.aiassistant.rag;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** 标题感知的 Markdown 切分器，保留章节路径、表格和稳定片段 ID。 */
@Component
public class DocumentSplitter {
    private static final int DEFAULT_MAX_SEGMENT_SIZE = 1200;
    private static final int DEFAULT_OVERLAP = 160;

    public List<Document> split(Document document) {
        return split(document, DEFAULT_MAX_SEGMENT_SIZE, DEFAULT_OVERLAP);
    }

    public List<Document> split(Document document, int maxSegmentSize, int overlap) {
        if (document == null || document.getText() == null || document.getText().isBlank()) return List.of();
        if (maxSegmentSize <= 0 || overlap < 0 || overlap >= maxSegmentSize) {
            throw new IllegalArgumentException("无效的切分参数");
        }
        List<SectionBlock> blocks = parseBlocks(document);
        List<Document> result = new ArrayList<>();
        int ordinal = 0;
        String activeHeading = null;
        StringBuilder merged = new StringBuilder();
        for (SectionBlock block : blocks) {
            boolean headingChanged = activeHeading != null && !activeHeading.equals(block.headingPath());
            boolean tooLarge = merged.length() > 0 && merged.length() + block.text().length() + 2 > maxSegmentSize;
            if (headingChanged || tooLarge) {
                ordinal = emit(merged.toString(), activeHeading, document, ordinal, maxSegmentSize, overlap, result);
                merged.setLength(0);
            }
            activeHeading = block.headingPath();
            if (merged.length() > 0) merged.append("\n\n");
            merged.append(block.text());
        }
        emit(merged.toString(), activeHeading, document, ordinal, maxSegmentSize, overlap, result);
        return result;
    }

    private List<SectionBlock> parseBlocks(Document document) {
        List<SectionBlock> blocks = new ArrayList<>();
        String[] headings = new String[6];
        StringBuilder block = new StringBuilder();
        String blockHeading = rootHeading(document);
        for (String line : document.getText().split("\\R", -1)) {
            int headingLevel = headingLevel(line);
            if (headingLevel > 0) {
                flush(blocks, block, blockHeading);
                headings[headingLevel - 1] = line.substring(headingLevel + 1).trim();
                Arrays.fill(headings, headingLevel, headings.length, null);
                blockHeading = headingPath(headings, document);
                continue;
            }
            if (line.isBlank()) {
                flush(blocks, block, blockHeading);
            } else {
                if (block.length() > 0) block.append('\n');
                block.append(line);
            }
        }
        flush(blocks, block, blockHeading);
        return blocks;
    }

    private void flush(List<SectionBlock> blocks, StringBuilder block, String heading) {
        String text = block.toString().trim();
        if (!text.isEmpty()) blocks.add(new SectionBlock(heading, text));
        block.setLength(0);
    }

    private int emit(String raw, String heading, Document source, int ordinal, int max, int overlap,
                     List<Document> target) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return ordinal;
        String prefix = heading == null || heading.isBlank() ? "" : "章节：" + heading + "\n";
        int bodyMax = Math.max(100, max - prefix.length());
        if (text.length() <= bodyMax) {
            target.add(newDocument(prefix + text, heading, source, ordinal++));
            return ordinal;
        }
        int step = Math.max(1, bodyMax - Math.min(overlap, bodyMax - 1));
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + bodyMax, text.length());
            target.add(newDocument(prefix + text.substring(start, end).trim(), heading, source, ordinal++));
            if (end == text.length()) break;
        }
        return ordinal;
    }

    private Document newDocument(String text, String heading, Document source, int ordinal) {
        String identity = value(source.getSource()) + "\n" + value(source.getSourceVersion()) + "\n"
                + value(heading) + "\n" + ordinal + "\n" + text;
        Document document = Document.builder().id(sha256(identity)).text(text).source(source.getSource())
                .sourceName(source.getSourceName()).sourceVersion(source.getSourceVersion())
                .documentId(source.getDocumentId()).revisionId(source.getRevisionId()).format(source.getFormat())
                .headingPath(heading).trustLevel(source.getTrustLevel())
                .metadata(new java.util.HashMap<>(source.getMetadata())).build();
        document.getMetadata().put("ordinal", ordinal);
        return document;
    }

    private int headingLevel(String line) {
        int count = 0;
        while (count < line.length() && count < 6 && line.charAt(count) == '#') count++;
        return count > 0 && count < line.length() && line.charAt(count) == ' ' ? count : 0;
    }

    private String headingPath(String[] headings, Document document) {
        List<String> parts = Arrays.stream(headings).filter(value -> value != null && !value.isBlank()).toList();
        return parts.isEmpty() ? rootHeading(document) : String.join(" > ", parts);
    }

    private String rootHeading(Document document) {
        return document.getHeadingPath() == null ? document.getSourceName() : document.getHeadingPath();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成知识片段 ID", e);
        }
    }

    private String value(String value) { return value == null ? "" : value; }
    private record SectionBlock(String headingPath, String text) {}
}
