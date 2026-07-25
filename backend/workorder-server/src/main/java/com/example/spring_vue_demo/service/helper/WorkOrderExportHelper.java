package com.example.spring_vue_demo.service.helper;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.read.listener.PageReadListener;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.example.spring_vue_demo.vo.HandleUserInfoVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderExportVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderPageVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author wtt
 * @date 2025/06/16
 */
@Slf4j
@Component
public class WorkOrderExportHelper {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void processExportData(List<WorkOrderPageVO> pageVOS, List<WorkOrderExportVO> excelVOS) {
        Map<String, List<HandleUserInfoVO>> collectHandle = pageVOS.stream()
                .filter(vo -> vo.getHandlerInfo() != null)
                .collect(Collectors.toMap(WorkOrderPageVO::getCode, WorkOrderPageVO::getHandlerInfo));

        Map<String, List<HandleUserInfoVO>> collectAudit = pageVOS.stream()
                .filter(vo -> vo.getAuditorInfo() != null)
                .collect(Collectors.toMap(WorkOrderPageVO::getCode, WorkOrderPageVO::getAuditorInfo));

        for (WorkOrderExportVO exportVO : excelVOS) {
            String code = exportVO.getCode();
            List<HandleUserInfoVO> handleInfos = collectHandle.getOrDefault(code, null);
            List<HandleUserInfoVO> auditInfos = collectAudit.getOrDefault(code, null);

            if (CollectionUtils.isNotEmpty(handleInfos)) {
                LocalDateTime maxHandleTime = handleInfos.stream()
                        .filter(HandleUserInfoVO::getFinished)
                        .map(HandleUserInfoVO::getHandleTime)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);

                exportVO.setFinishTime(maxHandleTime != null ? formatter.format(maxHandleTime) : null);
            }

            if (CollectionUtils.isNotEmpty(auditInfos)) {
                LocalDateTime maxAuditTime = auditInfos.stream()
                        .filter(HandleUserInfoVO::getFinished)
                        .map(HandleUserInfoVO::getHandleTime)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);

                exportVO.setFinishedAuditTime(maxAuditTime != null ? formatter.format(maxAuditTime) : null);
            }
        }

    }

    // 导出到单独文件
    public void exportToSingleFile(List<WorkOrderExportVO> excelVOS, int pageNum) {
        String fileName = formatterDate.format(LocalDateTime.now()) + "工单_" + pageNum + ".xlsx";
        String tmpDir = System.getProperty("java.io.tmpdir");
        String filePath = tmpDir + File.separator + fileName;//临时文件

        try {
            EasyExcel.write(filePath, WorkOrderExportVO.class)
                    .sheet("工单信息_" + pageNum)
                    .doWrite(excelVOS);
        } catch (Exception e) {
            log.error("导出文件{}失败", fileName, e);
            throw new RuntimeException("导出文件失败", e);
        }
    }

    public void mergeFiles(HttpServletResponse response, int totalPages) throws IOException {
        String baseFileName = formatterDate.format(LocalDateTime.now()) + "工单";
        String mergedFileName = baseFileName + (totalPages > 1 ? "_合并.zip" : ".xlsx");

        response.setContentType(totalPages > 1 ?
                "application/zip" : "application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition",
                "attachment;filename=" + URLEncoder.encode(mergedFileName, "UTF-8"));

        // 如果只有一个文件，直接返回原文件
        if (totalPages == 1) {
            String tmpDir = System.getProperty("java.io.tmpdir");
            String filePath = tmpDir + File.separator + baseFileName + "_1.xlsx";
            File file = new File(filePath);

            if (file.exists()) {
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = response.getOutputStream()) {
                    IOUtils.copy(in, out);
                } finally {
                    file.delete();
                }
            }
            return;
        }

        // 多个文件时创建压缩包
        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
            for (int i = 1; i <= totalPages; i++) {
                String fileName = baseFileName + "_" + i + ".xlsx";
                String tmpDir = System.getProperty("java.io.tmpdir");
                String filePath = tmpDir + File.separator + fileName;
                File file = new File(filePath);

                if (file.exists()) {
                    // 添加文件到压缩包
                    zipOut.putNextEntry(new ZipEntry(fileName));

                    try (InputStream in = new FileInputStream(file)) {
                        IOUtils.copy(in, zipOut);
                    }

                    zipOut.closeEntry();
                    file.delete();
                }
            }
        }
    }
}

