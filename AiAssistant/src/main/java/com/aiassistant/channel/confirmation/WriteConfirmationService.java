package com.aiassistant.channel.confirmation;

import com.aiassistant.tools.CliExecutorTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;

@Service @RequiredArgsConstructor @Slf4j
public class WriteConfirmationService {
    private final MongoTemplate mongo;
    @Value("${workorder.channel.confirmation-ttl:PT10M}") private Duration ttl;
    @Value("${workorder.channel.confirmation-retention:P7D}") private Duration retention;

    public WriteConfirmation create(Long sessionId,String userId,String tenant,String conversation,String openId,String dryRun,String summary,String traceId){
        String command=removeDryRun(dryRun); Instant now=Instant.now();
        WriteConfirmation value=WriteConfirmation.builder().operationId(UUID.randomUUID().toString()).sessionId(sessionId).userId(userId)
                .tenantId(tenant).conversationId(conversation).requesterOpenId(openId).command(command).commandDigest(digest(command))
                .summary(summary).status(WriteConfirmation.Status.PENDING).createdAt(now).expiresAt(now.plus(ttl)).purgeAt(now.plus(retention)).traceId(traceId).build();
        mongo.save(value); audit(value,"created"); return value;
    }
    public Decision claim(String operationId,String tenant,String conversation,String openId,boolean confirm){
        WriteConfirmation current=mongo.findById(operationId,WriteConfirmation.class);
        if(current==null)return new Decision(false,"确认记录不存在",null);
        if(!eq(current.getTenantId(),tenant)||!eq(current.getConversationId(),conversation)||!eq(current.getRequesterOpenId(),openId))return new Decision(false,"只能由原请求人在原会话确认",current);
        if(current.getExpiresAt().isBefore(Instant.now())){transition(operationId,WriteConfirmation.Status.PENDING,WriteConfirmation.Status.EXPIRED,"expired");return new Decision(false,"确认已过期",current);}
        WriteConfirmation.Status target=confirm?WriteConfirmation.Status.EXECUTING:WriteConfirmation.Status.CANCELLED;
        WriteConfirmation claimed=transition(operationId,WriteConfirmation.Status.PENDING,target,confirm?"claimed":"cancelled");
        return claimed==null?new Decision(false,"该操作已经处理，不能重复确认",current):new Decision(true,confirm?"已确认":"已取消",claimed);
    }
    public void complete(String id,WriteConfirmation.Status status,String result){
        Query q=Query.query(Criteria.where("operationId").is(id).and("status").is(WriteConfirmation.Status.EXECUTING));
        Update u=new Update().set("status",status).set("decidedAt",Instant.now()).set("resultSummary",truncate(result));
        mongo.updateFirst(q,u,WriteConfirmation.class);
        WriteConfirmation value=mongo.findById(id,WriteConfirmation.class);if(value!=null)audit(value,status.name());
    }
    private WriteConfirmation transition(String id,WriteConfirmation.Status from,WriteConfirmation.Status to,String action){
        Query q=Query.query(Criteria.where("operationId").is(id).and("status").is(from));Update u=new Update().set("status",to).set("decidedAt",Instant.now());
        var opts=org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true);WriteConfirmation v=mongo.findAndModify(q,u,opts,WriteConfirmation.class);if(v!=null)audit(v,action);return v;
    }
    static String removeDryRun(String c){return c.trim().replaceFirst("(?i)\\s+--dry-run(?=\\s|$)","").replaceAll("\\s+"," ");}
    static String digest(String c){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String truncate(String s){return s==null?"":s.substring(0,Math.min(500,s.length()));}
    private boolean eq(String a,String b){return java.util.Objects.equals(a,b);}
    private void audit(WriteConfirmation v,String action){log.info("WRITE-CONFIRM-AUDIT operationId={} userId={} channel=FEISHU conversationId={} digest={} action={} status={} traceId={}",v.getOperationId(),v.getUserId(),v.getConversationId(),v.getCommandDigest(),action,v.getStatus(),v.getTraceId());}
    public record Decision(boolean accepted,String message,WriteConfirmation confirmation){}
}
