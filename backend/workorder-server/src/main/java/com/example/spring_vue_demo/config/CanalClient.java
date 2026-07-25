package com.example.spring_vue_demo.config;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.service.ElasticSearchService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author wtt
 * @date 2026/03/14
 */
@Component
@Slf4j
public class CanalClient {

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Value("${canal.host:127.0.0.1}")
    private String canalHost;

    @Value("${canal.port:11111}")
    private Integer canalPort;

    @Value("${canal.destination:example}")
    private String destination;

    @Value("${canal.username:}")
    private String username;

    @Value("${canal.password:}")
    private String password;

    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        new Thread(this::startCanalClient).start();
    }

    public void startCanalClient() {
        CanalConnector connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalHost, canalPort),
                destination,
                username,
                password
        );

        int batchSize = 1000;
        int emptyCount = 0;

        try {
            connector.connect();
            connector.subscribe("wos\\.work_order");  // 订阅工单表
            connector.rollback();

            log.info("Canal client started successfully");

            while (running) {
                try {
                    Message message = connector.getWithoutAck(batchSize);
                    long batchId = message.getId();
                    int size = message.getEntries().size();

                    if (batchId == -1 || size == 0) {
                        emptyCount++;

                        if (emptyCount % 10 == 0) {
                            log.debug("Empty message count: {}", emptyCount);
                        }

                        Thread.sleep(1000);
                        continue;
                    }

                    emptyCount = 0;
                    processEntries(message.getEntries());
                    connector.ack(batchId);

                } catch (Exception e) {
                    log.error("Process canal message error", e);
                    connector.rollback();
                }
            }
        } catch (Exception e) {
            log.error("Canal client error", e);
        } finally {
            connector.disconnect();
        }
    }

    private void processEntries(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN ||
                    entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            try {
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                CanalEntry.EventType eventType = rowChange.getEventType();
                String tableName = entry.getHeader().getTableName();

                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    switch (eventType) {
                        case INSERT:
                            handleInsert(rowData);
                            break;
                        case UPDATE:
                            handleUpdate(rowData);
                            break;
                        case DELETE:
                            handleDelete(rowData);
                            break;
                        default:
                            log.debug("Ignore event type: {}", eventType);
                    }
                }
            } catch (Exception e) {
                log.error("Process entry error", e);
            }
        }
    }

    private void handleInsert(CanalEntry.RowData rowData) {
        WorkOrder workOrder = convertToWorkOrder(rowData.getAfterColumnsList());
        elasticSearchService.indexWorkOrder(workOrder);
        log.info("Indexed new work order: {}", workOrder.getId());
    }

    private void handleUpdate(CanalEntry.RowData rowData) {
        WorkOrder workOrder = convertToWorkOrder(rowData.getAfterColumnsList());
        elasticSearchService.updateWorkOrder(workOrder);
        log.info("Updated work order: {}", workOrder.getId());
    }

    private void handleDelete(CanalEntry.RowData rowData) {
        String id = getColumnValue(rowData.getBeforeColumnsList(), "id");
        elasticSearchService.deleteWorkOrder(id);
        log.info("Deleted work order: {}", id);
    }

    private WorkOrder convertToWorkOrder(List<CanalEntry.Column> columns) {
        WorkOrder workOrder = new WorkOrder();
        for (CanalEntry.Column column : columns) {
            if (column.getIsNull()) continue;

            switch (column.getName()) {
                case "id":
                    workOrder.setId(Long.parseLong(column.getValue()));
                    break;
                case "title":
                    workOrder.setTitle(column.getValue());
                    break;
                case "content":
                    workOrder.setContent(column.getValue());
                    break;
                case "create_time":
                    workOrder.setCreateTime(LocalDateTime.parse(column.getValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    break;
                // 添加其他字段映射
            }
        }
        return workOrder;
    }

    private String getColumnValue(List<CanalEntry.Column> columns, String columnName) {
        return columns.stream()
                .filter(col -> col.getName().equals(columnName))
                .findFirst()
                .map(CanalEntry.Column::getValue)
                .orElse(null);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(value, formatter);
    }
}
