package com.aiassistant.channel.confirmation;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WriteConfirmationServiceTest {
 @Test void createRemovesDryRunAndPersistsDigestWithoutCredentials(){
  MongoTemplate mongo=mock(MongoTemplate.class);WriteConfirmationService service=new WriteConfirmationService(mongo);ReflectionTestUtils.setField(service,"ttl",Duration.ofMinutes(10));ReflectionTestUtils.setField(service,"retention",Duration.ofDays(7));
  when(mongo.save(any(WriteConfirmation.class))).thenAnswer(i->i.getArgument(0));
  WriteConfirmation c=service.create(7L,"user-1","tenant","chat","open","workorder-cli --dry-run work_order_delete --id 9","delete id=9","trace");
  assertFalse(c.getCommand().contains("--dry-run"));assertEquals(64,c.getCommandDigest().length());assertEquals(WriteConfirmation.Status.PENDING,c.getStatus());assertTrue(c.getExpiresAt().isAfter(c.getCreatedAt()));verify(mongo).save(c);
 }
 @Test void wrongRequesterIsRejectedBeforeAtomicClaim(){
  MongoTemplate mongo=mock(MongoTemplate.class);WriteConfirmationService service=new WriteConfirmationService(mongo);
  WriteConfirmation c=WriteConfirmation.builder().operationId("op").tenantId("t").conversationId("chat").requesterOpenId("owner").expiresAt(java.time.Instant.now().plusSeconds(60)).status(WriteConfirmation.Status.PENDING).build();when(mongo.findById("op",WriteConfirmation.class)).thenReturn(c);
  var result=service.claim("op","t","chat","attacker",true);assertFalse(result.accepted());assertTrue(result.message().contains("原请求人"));verify(mongo,never()).findAndModify(any(),any(),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(WriteConfirmation.class));
 }
 @Test void commandNormalizationIsStable(){assertEquals("workorder-cli delete --id 1",WriteConfirmationService.removeDryRun(" workorder-cli   --dry-run delete --id 1 "));assertEquals(WriteConfirmationService.digest("same"),WriteConfirmationService.digest("same"));}
}
