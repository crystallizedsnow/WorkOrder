package com.aiassistant.tools;

import com.aiassistant.tool.annotation.P;
import com.aiassistant.tool.annotation.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FileTools {

    @Value("${workorder.file.output-dir:./output/}")
    private String outputDir;

    @Tool("创建Excel文件并写入数据")
    public String createExcelFile(@P("filepath") String filepath,
                                   @P("headers") List<String> headers,
                                   @P("rows") List<List<Object>> rows) throws IOException {
        Path path = validatePath(filepath);
        Files.createDirectories(path.getParent());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<Object> rowData = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                    Cell cell = row.createCell(colIndex);
                    Object value = rowData.get(colIndex);
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else {
                        cell.setCellValue(value != null ? value.toString() : "");
                    }
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                workbook.write(fos);
            }
        }

        return "Excel文件创建成功: " + path.toAbsolutePath();
    }

    @Tool("读取Excel文件内容")
    public String readExcelFile(@P("filepath") String filepath) throws IOException {
        Path path = validatePath(filepath);
        if (!Files.exists(path)) {
            return "文件不存在: " + filepath;
        }

        StringBuilder result = new StringBuilder();
        
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(path.toFile()))) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    cells.add(getCellValueAsString(cell));
                }
                result.append(String.join("\t", cells)).append("\n");
            }
        }

        return result.toString();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    @Tool("创建Markdown文件")
    public String createMarkdownFile(@P("filepath") String filepath,
                                      @P("content") String content) throws IOException {
        Path path = validatePath(filepath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return "Markdown文件创建成功: " + path.toAbsolutePath();
    }

    @Tool("读取Markdown文件")
    public String readMarkdownFile(@P("filepath") String filepath) throws IOException {
        Path path = validatePath(filepath);
        if (!Files.exists(path)) {
            return "文件不存在: " + filepath;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Tool("创建TXT文件")
    public String createTxtFile(@P("filepath") String filepath,
                                 @P("content") String content) throws IOException {
        Path path = validatePath(filepath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        log.info("TXT文件创建成功: {}", path.toAbsolutePath());
        return "TXT文件创建成功: " + path.toAbsolutePath();
    }

    @Tool("读取TXT文件")
    public String readTxtFile(@P("filepath") String filepath) throws IOException {
        Path path = validatePath(filepath);
        if (!Files.exists(path)) {
            return "文件不存在: " + filepath;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
    
    @Tool("创建TXT文件（别名方法，兼容filename参数）")
    public String createTxtFileByName(@P("filename") String filename,
                                       @P("content") String content) throws IOException {
        return createTxtFile(filename, content);
    }

    private Path validatePath(String filepath) {
        if (filepath.contains("..")) {
            throw new IllegalArgumentException("文件路径不能包含 '..'");
        }
        
        Path path = Paths.get(filepath);
        if (!path.isAbsolute()) {
            path = Paths.get(outputDir).resolve(filepath);
        }
        
        return path.normalize();
    }
}
