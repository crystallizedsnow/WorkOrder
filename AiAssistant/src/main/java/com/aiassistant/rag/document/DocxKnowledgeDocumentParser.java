package com.aiassistant.rag.document;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Safe, dependency-free DOCX parser for headings, paragraphs, lists and tables. */
@Component
public class DocxKnowledgeDocumentParser implements KnowledgeDocumentParser {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final long MAX_UNCOMPRESSED = 30L * 1024 * 1024;

    public boolean supports(String fileName, String contentType) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".docx");
    }
    public String format() { return "DOCX"; }
    public String version() { return "docx-ooxml-v1"; }

    public ParsedKnowledgeDocument parse(InputStream input) {
        try {
            Map<String, byte[]> parts = unzip(input);
            byte[] documentXml = parts.get("word/document.xml");
            if (documentXml == null) throw new IllegalArgumentException("DOCX 缺少 word/document.xml");
            Map<String, String> styles = parseStyles(parts.get("word/styles.xml"));
            Document xml = parseXml(documentXml);
            Element body = (Element) xml.getElementsByTagNameNS(W, "body").item(0);
            StringBuilder out = new StringBuilder();
            String title = coreTitle(parts.get("docProps/core.xml"));
            for (Node node = body.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element element)) continue;
                if ("p".equals(element.getLocalName())) {
                    String value = text(element).trim();
                    if (value.isEmpty()) continue;
                    String style = paragraphStyle(element, styles);
                    int heading = headingLevel(style);
                    if (title == null && (heading == 1 || "Title".equalsIgnoreCase(style))) title = value;
                    if (heading > 0) out.append("#".repeat(heading)).append(' ').append(value);
                    else if (isList(element) || style.toLowerCase(Locale.ROOT).startsWith("list"))
                        out.append("- ").append(value);
                    else out.append(value);
                    out.append("\n\n");
                } else if ("tbl".equals(element.getLocalName())) {
                    appendTable(element, out);
                }
            }
            String normalized = out.toString().replaceAll("[ \\t]+\\n", "\n")
                    .replaceAll("\\n{3,}", "\n\n").trim();
            if (normalized.isBlank()) throw new IllegalArgumentException("Word 文档没有可提取的正文");
            List<String> warnings = new ArrayList<>();
            if (normalized.length() < 100) warnings.add("提取正文少于100字符，请检查文档是否主要由图片或文本框构成");
            return new ParsedKnowledgeDocument(title == null ? "未命名文档" : title, normalized, warnings);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("DOCX 解析失败: " + e.getMessage(), e);
        }
    }

    private Map<String, byte[]> unzip(InputStream input) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) throw new IllegalArgumentException("DOCX 包含非法路径");
                byte[] value = zip.readAllBytes();
                total += value.length;
                if (total > MAX_UNCOMPRESSED) throw new IllegalArgumentException("DOCX 解压内容超过限制");
                if (name.equals("word/document.xml") || name.equals("word/styles.xml")
                        || name.equals("docProps/core.xml")) parts.put(name, value);
            }
        }
        return parts;
    }

    private Document parseXml(byte[] value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(value));
    }

    private Map<String, String> parseStyles(byte[] value) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (value == null) return result;
        Document xml = parseXml(value);
        NodeList nodes = xml.getElementsByTagNameNS(W, "style");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element style = (Element) nodes.item(i);
            String id = style.getAttributeNS(W, "styleId");
            NodeList names = style.getElementsByTagNameNS(W, "name");
            if (names.getLength() > 0) result.put(id, ((Element) names.item(0)).getAttributeNS(W, "val"));
        }
        return result;
    }

    private String coreTitle(byte[] value) throws Exception {
        if (value == null) return null;
        Document xml = parseXml(value);
        NodeList titles = xml.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title");
        if (titles.getLength() == 0 || titles.item(0).getTextContent().isBlank()) return null;
        return titles.item(0).getTextContent().trim();
    }

    private String paragraphStyle(Element paragraph, Map<String, String> styles) {
        NodeList nodes = paragraph.getElementsByTagNameNS(W, "pStyle");
        if (nodes.getLength() == 0) return "";
        String id = ((Element) nodes.item(0)).getAttributeNS(W, "val");
        return styles.getOrDefault(id, id);
    }

    private int headingLevel(String style) {
        if (style == null) return 0;
        String normalized = style.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.startsWith("heading") || normalized.startsWith("标题")) {
            String digits = normalized.replaceAll("\\D+", "");
            if (!digits.isBlank()) return Math.max(1, Math.min(6, Integer.parseInt(digits)));
        }
        return 0;
    }

    private boolean isList(Element paragraph) {
        return paragraph.getElementsByTagNameNS(W, "numPr").getLength() > 0;
    }

    private String text(Element element) {
        StringBuilder result = new StringBuilder();
        NodeList texts = element.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < texts.getLength(); i++) result.append(texts.item(i).getTextContent());
        return result.toString();
    }

    private void appendTable(Element table, StringBuilder out) {
        NodeList rows = table.getElementsByTagNameNS(W, "tr");
        for (int row = 0; row < rows.getLength(); row++) {
            NodeList cells = ((Element) rows.item(row)).getElementsByTagNameNS(W, "tc");
            List<String> values = new ArrayList<>();
            for (int col = 0; col < cells.getLength(); col++) values.add(text((Element) cells.item(col)).trim());
            out.append("| ").append(String.join(" | ", values)).append(" |\n");
            if (row == 0) out.append("| ").append("--- | ".repeat(Math.max(1, values.size()))).append("\n");
        }
        out.append('\n');
    }
}
