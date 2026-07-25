package com.aiassistant.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;



/**
 * @author wtt
 * @date 2026/02/27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessages {
    //映射到MongoDB的id字段
    @Id
    private ObjectId messageId;

    private String memoryId;

    //存储当前聊天记录的json字段
    private String content;
}
