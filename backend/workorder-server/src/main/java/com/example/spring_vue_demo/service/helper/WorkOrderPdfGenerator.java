package com.example.spring_vue_demo.service.helper;

/**
 * @author wtt
 * @date 2025/06/13
 */

import com.example.spring_vue_demo.vo.HandleUserInfoVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderDetailVO;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

public class WorkOrderPdfGenerator {

    private static Font chineseFont;
    private static Font chineseBoldFont;
    private static Font chineseTitleFont;

    static {
        try {
            // 使用系统字体或指定字体文件路径
            BaseFont bfChinese = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            chineseFont = new Font(bfChinese, 12, Font.NORMAL);
            chineseBoldFont = new Font(bfChinese, 12, Font.BOLD);
            chineseTitleFont = new Font(bfChinese, 16, Font.BOLD);
        } catch (DocumentException | IOException e) {
            e.printStackTrace();
            // 回退到默认字体
            chineseFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            chineseBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            chineseTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        }
    }

    /**
     * 生成工单PDF字节流
     */
    public static ByteArrayOutputStream generateWorkOrderPdf(WorkOrderDetailVO workOrder) {
        // 创建文档对象，设置页面大小和边距
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                PdfWriter.getInstance(document, outputStream);

                document.open();

                // 1. 添加基础信息部分
                addBasicInfo(document, workOrder);

                // 2. 添加详情部分
                addContentInfo(document, workOrder);

                // 3. 添加操作节点部分
                addOperationNodes(document, workOrder);
            } catch (DocumentException e) {
                throw new RuntimeException(e);
            } finally {
                document.close();
            }
            return outputStream;
        }

        /**
         * 添加基础信息部分
         */
        private static void addBasicInfo (Document document, WorkOrderDetailVO workOrder) throws DocumentException {
            // 标题
            Paragraph title = new Paragraph(workOrder.getTitle(), chineseTitleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20f);
            document.add(title);

            // 创建表格，4列
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // 设置表格列宽比例
            float[] columnWidths = {1.5f, 4f, 1.5f, 4f};
            table.setWidths(columnWidths);

            // 添加表格内容
            addTableRow(table, "工单编号:", workOrder.getCode());
            addTableRow(table, "工单类型:", workOrder.getTypeDesc());
            addTableRow(table, "优先级:", workOrder.getPriorityLevelDesc());
            addTableRow(table, "状态:", workOrder.getStatusDesc());
            addTableRow(table, "创建时间:", workOrder.getCreateTime());
            addTableRow(table, "更新时间:", workOrder.getUpdateTime());
            addTableRow(table, "截止时间:", workOrder.getDeadlineTime());
            addTableRow(table,"取消时间：",workOrder.getCancelTime());
            addTableRow(table,"删除时间：",workOrder.getDeleteTime());
            addTableRow(table," "," ");
            document.add(table);
        }

        /**
         * 添加详情部分
         */
        private static void addContentInfo (Document document, WorkOrderDetailVO workOrder) throws DocumentException {
            // 详情标题
            Paragraph contentTitle = new Paragraph("工单详情", chineseBoldFont);
            contentTitle.setSpacingBefore(15f);
            contentTitle.setSpacingAfter(10f);
            document.add(contentTitle);

            // 详情内容
            if (workOrder.getContent() != null && !workOrder.getContent().isEmpty()) {
                Paragraph content = new Paragraph(workOrder.getContent(), chineseFont);
                content.setSpacingAfter(15f);
                document.add(content);
            } else {
                Paragraph noContent = new Paragraph("无详情内容", chineseFont);
                noContent.setSpacingAfter(15f);
                document.add(noContent);
            }
        }

        /**
         * 添加操作节点部分
         */
        private static void addOperationNodes (Document document, WorkOrderDetailVO workOrder) throws DocumentException
        {
            // 操作节点标题
            Paragraph nodesTitle = new Paragraph("操作节点记录", chineseBoldFont);
            nodesTitle.setSpacingBefore(15f);
            nodesTitle.setSpacingAfter(10f);
            document.add(nodesTitle);

            // 提交信息
            addOperationNode(document, workOrder.getSubmitterInfo(), "提交信息");

            // 审批信息
            if (workOrder.getAuditorInfo() != null && !workOrder.getAuditorInfo().isEmpty()) {
                for (HandleUserInfoVO auditor : workOrder.getAuditorInfo()) {
                    addOperationNode(document, auditor, "审批信息");
                }
            }

            // 派单信息
            if (workOrder.getDistributerInfo() != null) {
                addOperationNode(document, workOrder.getDistributerInfo(), "派单信息");
            }

            // 处理信息
            if (workOrder.getHandlerInfo() != null && !workOrder.getHandlerInfo().isEmpty()) {
                for (HandleUserInfoVO handler : workOrder.getHandlerInfo()) {
                    addOperationNode(document, handler, "处理信息");
                }
            }

            // 确认信息
            if (workOrder.getCheckerInfo() != null) {
                addOperationNode(document, workOrder.getCheckerInfo(), "确认信息");
            }
        }

        /**
         * 添加单个操作节点
         */
        private static void addOperationNode (Document document, HandleUserInfoVO node, String nodeType) throws
        DocumentException {
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // 设置表格列宽比例
            float[] columnWidths = {1f, 4f};
            table.setWidths(columnWidths);

            // 节点类型标题
            PdfPCell typeCell = new PdfPCell(new Paragraph(nodeType, chineseBoldFont));
            typeCell.setColspan(2);
            typeCell.setBackgroundColor(new BaseColor(220, 220, 220));
            typeCell.setBorder(Rectangle.NO_BORDER);
            typeCell.setPadding(5f);
            table.addCell(typeCell);

            // 操作人信息
            addDetailRow(table, "操作人:",node!=null?node.getUserName():null);
            addDetailRow(table, "公司:", node!=null?node.getCompanyName():null);
            addDetailRow(table, "部门:", node!=null?node.getDepartmentName():null);
            addDetailRow(table, "操作类型:", node!=null?node.getHandleTypeDesc():null);
            addDetailRow(table, "操作时间:", node!=null && node.getHandleTime()!=null?node.getHandleTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")):null);
            addDetailRow(table, "状态:", node!=null?node.getFinishedDesc():null);
            addDetailRow(table,"备注：",node!=null?node.getRemark():null);

            // 备注
//            PdfPCell remarkLabelCell = new PdfPCell(new Paragraph("备注:", chineseBoldFont));
//            remarkLabelCell.setBorder(Rectangle.NO_BORDER);
//            table.addCell(remarkLabelCell);
//
//            PdfPCell remarkValueCell = new PdfPCell(new Paragraph(node!=null?node.getRemark():null, chineseFont));
//            remarkValueCell.setBorder(Rectangle.NO_BORDER);
//            table.addCell(remarkValueCell);

            document.add(table);
        }

        /**
         * 添加表格行
         */
        private static void addTableRow (PdfPTable table, String label, String value){
            PdfPCell labelCell = new PdfPCell(new Paragraph(label, chineseBoldFont));
            labelCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Paragraph(value != null ? value : "无", chineseFont));
            valueCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(valueCell);
        }

        /**
         * 添加详情行
         */
        private static void addDetailRow (PdfPTable table, String label, String value){
            PdfPCell labelCell = new PdfPCell(new Paragraph(label, chineseBoldFont));
            labelCell.setBorder(Rectangle.NO_BORDER);
            labelCell.setPaddingLeft(10f);
            table.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Paragraph(value != null ? value : "无", chineseFont));
            valueCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(valueCell);
        }

        /**
         * 添加水平分隔线
         */
        private static void addHorizontalLine (Document document) throws DocumentException {
            Paragraph line = new Paragraph();
            line.add(new Chunk(new LineSeparator()));
            line.setSpacingBefore(10f);
            line.setSpacingAfter(10f);
            document.add(line);
        }
    }


