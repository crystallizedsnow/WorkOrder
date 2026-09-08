-- MySQL dump 10.13  Distrib 9.0.1, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: wos
-- ------------------------------------------------------
-- Server version	9.0.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `company`
--

DROP TABLE IF EXISTS `company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公司ID',
  `name` varchar(100) NOT NULL COMMENT '公司名称',
  `code` varchar(50) DEFAULT NULL COMMENT '公司编码',
  `parent_company_code` varchar(50) DEFAULT NULL COMMENT '上级公司编码',
  `level` tinyint DEFAULT '1' COMMENT '公司层级（1：总部，2：省公司，3：市公司）',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company`
--

LOCK TABLES `company` WRITE;
/*!40000 ALTER TABLE `company` DISABLE KEYS */;
INSERT INTO `company` VALUES (2,'总公司','91440000UM3RBJQT7U',NULL,0,1749833029,1749833029),(3,'广东省公司','91440000JJVF8Q2LQ0','91440000UM3RBJQT7U',1,1749833079,1749833079),(4,'广州市公司','91440000LGT8QKPUH2','91440000JJVF8Q2LQ0',2,1749833157,1749833157);
/*!40000 ALTER TABLE `company` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `department`
--

DROP TABLE IF EXISTS `department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `name` varchar(100) NOT NULL COMMENT '部门名称',
  `code` varchar(50) DEFAULT NULL COMMENT '部门编码',
  `parent_department_code` varchar(50) DEFAULT NULL COMMENT '上级部门ID(null表示顶级）',
  `company_code` varchar(50) NOT NULL COMMENT '所属公司编码',
  `leader_number` varchar(50) DEFAULT NULL COMMENT '部门主管员工工号',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `department`
--

LOCK TABLES `department` WRITE;
/*!40000 ALTER TABLE `department` DISABLE KEYS */;
INSERT INTO `department` VALUES (1,'总务部','D91440000UM3RBJQT7U-0001',NULL,'91440000UM3RBJQT7U','E20250614T153733789',1749971341,1749971504),(2,'技术部','D91440000UM3RBJQT7U-0002',NULL,'91440000UM3RBJQT7U','E20250614T153859814',1749971347,1749971544),(3,'人力部','D91440000UM3RBJQT7U-0003',NULL,'91440000UM3RBJQT7U','E20250614T154008018',1749971373,1749971556),(4,'财务部','D91440000UM3RBJQT7U-0004',NULL,'91440000UM3RBJQT7U','E20250614T154038035',1749971384,1749971569),(5,'运维部','D91440000UM3RBJQT7U-0005',NULL,'91440000UM3RBJQT7U','E20250614T154058921',1749971401,1749971579),(6,'总务部','D91440000JJVF8Q2LQ0-0001','D91440000UM3RBJQT7U-0001','91440000JJVF8Q2LQ0','E20250615T152329989',1749971849,1749973110),(7,'技术部','D91440000JJVF8Q2LQ0-0002','D91440000UM3RBJQT7U-0002','91440000JJVF8Q2LQ0','E20250615T152402493',1749971985,1749973120),(8,'人力部','D91440000JJVF8Q2LQ0-0003','D91440000UM3RBJQT7U-0003','91440000JJVF8Q2LQ0','E20250615T152425356',1749972016,1749973130),(9,'财务部','D91440000JJVF8Q2LQ0-0004','D91440000UM3RBJQT7U-0004','91440000JJVF8Q2LQ0','E20250615T152445862',1749972025,1749973145),(10,'运维部','D91440000JJVF8Q2LQ0-0005','D91440000UM3RBJQT7U-0005','91440000JJVF8Q2LQ0','E20250615T152457641',1749972042,1749973155),(11,'总务部','D91440000LGT8QKPUH2-0001','D91440000JJVF8Q2LQ0-0001','91440000LGT8QKPUH2','E20250615T152934618',1749972068,1749973174),(12,'技术部','D91440000LGT8QKPUH2-0002','D91440000JJVF8Q2LQ0-0002','91440000LGT8QKPUH2','E20250615T152945501',1749972074,1749973197),(13,'人力部','D91440000LGT8QKPUH2-0003','D91440000JJVF8Q2LQ0-0003','91440000LGT8QKPUH2','E20250615T153028416',1749972083,1749973209),(14,'财务部','D91440000LGT8QKPUH2-0004','D91440000JJVF8Q2LQ0-0004','91440000LGT8QKPUH2','E20250615T153044196',1749972090,1749973219),(15,'运维部','D91440000LGT8QKPUH2-0005','D91440000JJVF8Q2LQ0-0005','91440000LGT8QKPUH2','E20250615T153102759',1749972101,1749973229);
/*!40000 ALTER TABLE `department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flow`
--

DROP TABLE IF EXISTS `flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `flow_id` bigint NOT NULL COMMENT '流程id',
  `flow_name` varchar(128) NOT NULL COMMENT '流程名',
  `node_type` tinyint NOT NULL COMMENT '节点类型:审批2，指派3，验收5',
  `handler_id` bigint NOT NULL COMMENT '处理人id',
  `handler_name` varchar(128) NOT NULL COMMENT '处理人名',
  `is_parallel` tinyint DEFAULT '0' COMMENT '是否并行处理',
  `head_flow_id` bigint DEFAULT NULL COMMENT '头节点id(只有审核有)',
  `next_flow_id` bigint DEFAULT NULL COMMENT '下一节点id(只有审核有)',
  `is_last_node` tinyint DEFAULT '0' COMMENT '是否为该流程终止节点',
  `create_time` bigint NOT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单流程定义表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flow`
--

LOCK TABLES `flow` WRITE;
/*!40000 ALTER TABLE `flow` DISABLE KEYS */;
INSERT INTO `flow` VALUES (37,1934199238728749056,'流程1',2,4,'王五',0,37,38,0,1749984008,1749984008),(38,1934199238728749056,'流程1',2,5,'张六',0,37,NULL,1,1749984008,1749984008),(39,1934199238728749056,'流程1',3,4,'王五',0,NULL,NULL,1,1749984008,NULL),(40,1934199238728749056,'流程1',5,4,'王五',0,NULL,NULL,1,1749984008,NULL);
/*!40000 ALTER TABLE `flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `handle_user_info`
--

DROP TABLE IF EXISTS `handle_user_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `handle_user_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `order_id` bigint NOT NULL COMMENT '工单id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_name` varchar(64) NOT NULL COMMENT '用户名',
  `handle_type` tinyint NOT NULL COMMENT '用户类型（1：提交，2：审核，3：派单，4：处理，5：确认）',
  `finished` tinyint NOT NULL COMMENT '已完成操作，0未完成，1已完成',
  `company_code` varchar(64) NOT NULL COMMENT '公司id',
  `company_name` varchar(128) NOT NULL COMMENT '公司名',
  `department_code` varchar(64) NOT NULL COMMENT '部门id',
  `department_name` varchar(128) NOT NULL COMMENT '部门名',
  `handle_time` bigint DEFAULT NULL COMMENT '处理时间',
  `create_time` bigint NOT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '修改时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除位',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单操作信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `handle_user_info`
--

LOCK TABLES `handle_user_info` WRITE;
/*!40000 ALTER TABLE `handle_user_info` DISABLE KEYS */;
INSERT INTO `handle_user_info` VALUES (36,6,4,'王五',1,1,'91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0003','人力部',1749984266,1749984266,1749984266,'算力不够啊',0),(37,6,4,'王五',3,1,'91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0003','人力部',1749985783,1749984266,NULL,'快快做！',0),(38,6,4,'王五',5,1,'91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0003','人力部',1749986017,1749984266,NULL,'做得好！',0),(39,6,4,'王五',2,1,'91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0003','人力部',1749984266,1749984266,1749984266,'我同意了',0),(40,6,5,'张六',2,1,'91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0004','财务部',1749984528,1749984528,1749984528,'我同意了',0),(41,6,6,'王七',4,1,'91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0005','运维部',1749985949,1749985783,NULL,'快快做！',0);
/*!40000 ALTER TABLE `handle_user_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `message`
--

DROP TABLE IF EXISTS `message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `receiver_id` bigint NOT NULL COMMENT '接收人id',
  `sender_id` bigint NOT NULL COMMENT '发送人id',
  `work_order_id` bigint DEFAULT NULL COMMENT '关联工单id',
  `type` int NOT NULL COMMENT '类型，',
  `content` text NOT NULL COMMENT '内容',
  `send_time` bigint DEFAULT NULL COMMENT '发送时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除位',
  PRIMARY KEY (`id`),
  KEY `idx_message_work_order_id` (`work_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=88 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `message`
--

LOCK TABLES `message` WRITE;
/*!40000 ALTER TABLE `message` DISABLE KEYS */;
INSERT INTO `message` VALUES (81,5,4,NULL,200,'编号为WO202506151934190493713960960的工单需要您审核',1749982848,0),(82,4,4,NULL,100,'编号为WO202506151934200318107717632的工单已成功创建，等待审核',1749984266,0),(83,4,4,NULL,200,'编号为WO202506151934200318107717632的工单需要您审核',1749984266,0),(84,5,4,NULL,200,'编号为WO202506151934200318107717632的工单需要您审核',1749984528,0),(85,6,4,NULL,400,'您收到编号为WO202506151934200318107717632的工单，请尽快处理。',1749985783,0),(86,4,6,NULL,500,'编号为WO202506151934200318107717632的工单需要您验收。',1749985949,0),(87,6,4,NULL,600,'您完成的编号为WO202506151934200318107717632的工单，已经验收成功。',1749986017,0);
/*!40000 ALTER TABLE `message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `staff`
--

DROP TABLE IF EXISTS `staff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `staff_number` varchar(128) NOT NULL COMMENT '工号',
  `password` varchar(100) NOT NULL COMMENT '密码（加密）',
  `name` varchar(100) NOT NULL COMMENT '姓名',
  `company_code` varchar(50) NOT NULL COMMENT '所属公司编号',
  `company` varchar(100) DEFAULT NULL COMMENT '所属公司',
  `department_code` varchar(50) DEFAULT NULL COMMENT '部门编号',
  `department` varchar(100) DEFAULT NULL COMMENT '部门',
  `position` varchar(100) DEFAULT NULL COMMENT '职位',
  `status` tinyint DEFAULT '0' COMMENT '状态：0 正常，1 休假，2 停职，3 离职',
  `manager_number` varchar(50) DEFAULT NULL COMMENT '直属领导工号',
  `manager_name` varchar(100) DEFAULT NULL COMMENT '直属领导姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '修改时间',
  `role` varchar(20) NOT NULL DEFAULT 'user',
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职工表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `staff`
--

LOCK TABLES `staff` WRITE;
/*!40000 ALTER TABLE `staff` DISABLE KEYS */;
INSERT INTO `staff` VALUES (2,'E20250614T153733789','newPassword123!','张三','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0001','总务部','总经理',0,'E20250614T153733789','张三','13812345678','zhangsan@example.com',1749886653,1749971504,'user'),(3,'E20250614T153859814','newPassword123!','李四','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0002','技术部','软件工程师',0,'E20250614T153859814','李四','13812345698','lisi@example.com',1749886739,1749971544,'user'),(4,'E20250614T154008018','123456','王五','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0003','人力部','人力总监',0,'E20250614T154008018','王五','12312312347','wangwu@example.com',1749886808,1749971556,'user'),(5,'E20250614T154038035','123456','张六','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0004','财务部','财务总监',0,'E20250614T154038035','张六','12312312348','zhangliu@example.com',1749886838,1749971569,'user'),(6,'E20250614T154058921','123456','王七','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0005','运维部','运维主管',0,'E20250614T154058921','王七','12312312349','wangqi@example.com',1749886858,1749971579,'user'),(11,'A10001','123456','管理员','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0001','总务部','管理员',0,'E20250614T153733789','张三','98765432102','admin@company.com',1749893717,1749971504,'admin'),(12,'E20250615T152329989','123456','aaa','91440000JJVF8Q2LQ0','广东省公司','D91440000JJVF8Q2LQ0-0001','总务部','总经理',0,'E20250615T152329989','aaa','12341234100','aaa@example.com',1749972209,1749973110,'user'),(13,'E20250615T152402493','123456','bbb','91440000JJVF8Q2LQ0','广东省公司','D91440000JJVF8Q2LQ0-0002','技术部','技术主管',0,'E20250615T152402493','bbb','12341234101','bbb@example.com',1749972242,1749973120,'user'),(14,'E20250615T152425356','123456','ccc','91440000JJVF8Q2LQ0','广东省公司','D91440000JJVF8Q2LQ0-0003','人力部','人力主管',0,'E20250615T152425356','ccc','12341234102','ccc@example.com',1749972265,1749973130,'user'),(15,'E20250615T152445862','123456','ddd','91440000JJVF8Q2LQ0','广东省公司','D91440000JJVF8Q2LQ0-0004','财务部','财务主管',0,'E20250615T152445862','ddd','12341234103','ddd@example.com',1749972285,1749973145,'user'),(16,'E20250615T152457641','123456','eee','91440000JJVF8Q2LQ0','广东省公司','D91440000JJVF8Q2LQ0-0004','财务部','财务主管',0,'E20250615T152445862','ddd','12341234104','eee@example.com',1749972297,1749973145,'user'),(17,'E20250615T152934618','123456','111','91440000LGT8QKPUH2','广州市公司','D91440000LGT8QKPUH2-0001','总务部','总经理',0,'E20250615T152934618','111','13812341234','111@example.com',1749972574,1749973174,'user'),(18,'E20250615T152945501','123456','222','91440000LGT8QKPUH2','广州市公司','D91440000LGT8QKPUH2-0001','技术部','技术主管',0,'E20250615T152934618','111','13812341235','222@example.com',1749972585,1749973174,'user'),(19,'E20250615T153028416','123456','333','91440000LGT8QKPUH2','广州市公司','D91440000LGT8QKPUH2-0003','人力部','人力主管',0,'E20250615T153028416','333','13812341236','333@example.com',1749972628,1749973209,'user'),(20,'E20250615T153044196','123456','444','91440000LGT8QKPUH2','广州市公司','D91440000LGT8QKPUH2-0004','财务部','财务主管',0,'E20250615T153044196','444','13812341237','444@example.com',1749972644,1749973219,'user'),(21,'E20250615T153102759','123456','555','91440000LGT8QKPUH2','广州市公司','D91440000LGT8QKPUH2-0005','运维部','运维主管',0,'E20250615T153102759','555','13812341238','555@example.com',1749972662,1749973229,'user'),(22,'E20250615T153236135','123456','test','91440000UM3RBJQT7U','总公司','D91440000UM3RBJQT7U-0002','技术部','高级开发工程师',0,'E20250614T153859814','李四','13812341239','test@example.com',1749972756,1749973010,'user');
/*!40000 ALTER TABLE `staff` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `work_order`
--

DROP TABLE IF EXISTS `work_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `code` varchar(64) NOT NULL COMMENT '编号',
  `type` tinyint NOT NULL COMMENT '类型',
  `title` varchar(128) DEFAULT NULL COMMENT '工单标题',
  `priority_level` tinyint DEFAULT NULL COMMENT '优先级',
  `status` int NOT NULL COMMENT '状态',
  `flow_id` bigint NOT NULL COMMENT '流程id',
  `create_time` bigint NOT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `cancel_time` bigint DEFAULT NULL COMMENT '取消时间',
  `delete_time` bigint DEFAULT NULL COMMENT '删除时间',
  `deadline_time` bigint DEFAULT NULL COMMENT '截止时间',
  `content` text COMMENT '详情',
  `accessory_url` varchar(128) DEFAULT NULL COMMENT '附件url',
  `accessory_name` varchar(128) DEFAULT NULL COMMENT '附件名称',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除位',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `work_order`
--

LOCK TABLES `work_order` WRITE;
/*!40000 ALTER TABLE `work_order` DISABLE KEYS */;
INSERT INTO `work_order` VALUES (6,'WO202506151934200318107717632',0,'租服务器',0,600,1934199238728749056,1749984266,1749984266,NULL,NULL,NULL,'算力不够啊',NULL,NULL,0);
/*!40000 ALTER TABLE `work_order` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-06-15 19:19:18
