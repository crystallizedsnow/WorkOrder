package com.aiassistant.channel;

import com.aiassistant.channel.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChannelRouterComponentTest {
 @Test void feishuEventTraversesIdentityTokenAgentAndReply() throws Exception {
  MessageDeduplicator dedup=(c,m)->true;ChannelSessionStore sessions=(m,u)->88L;FeishuIdentityClient ids=mock(FeishuIdentityClient.class);AgentGateway gateway=mock(AgentGateway.class);ConversationExecutor executor=new ConversationExecutor(2);ChannelRateLimiter limiter=new ChannelRateLimiter(10,java.time.Clock.systemUTC());CountDownLatch sent=new CountDownLatch(1);List<OutboundMessage> replies=new CopyOnWriteArrayList<>();ChannelAdapter adapter=new ChannelAdapter(){public ChannelType type(){return ChannelType.FEISHU;}public void send(OutboundMessage m){replies.add(m);sent.countDown();}};
  when(ids.find("tenant","union","open")).thenReturn(new FeishuIdentityClient.BindingResult(true,"***001","张*","BOUND"));when(ids.exchange(any(),any(),any(),any())).thenReturn(new FeishuIdentityClient.ProxyToken("proxy-token",Instant.now().plusSeconds(60),"user-1"));when(gateway.execute(any())).thenReturn(Flux.just("查询结果"));
  ChannelRouter router=new ChannelRouter(dedup,sessions,ids,gateway,executor,limiter,List.of(adapter));var inbound=new InboundMessage(ChannelType.FEISHU,"bot","tenant","chat","message","open","union","查询我的工单","text","trace",Instant.now(),Map.of());
  assertEquals(ChannelRouter.RouteResult.ACCEPTED,router.route(inbound));assertTrue(sent.await(2,TimeUnit.SECONDS));assertEquals("查询结果",replies.get(0).text());ArgumentCaptor<AgentRequest> captor=ArgumentCaptor.forClass(AgentRequest.class);verify(gateway).execute(captor.capture());assertEquals("proxy-token",captor.getValue().accessToken());assertEquals("user-1",captor.getValue().userId());executor.close();
 }
 @Test void duplicateNeverReachesAgent(){MessageDeduplicator dedup=(c,m)->false;ChannelRouter router=new ChannelRouter(dedup,mock(ChannelSessionStore.class),mock(FeishuIdentityClient.class),mock(AgentGateway.class),new ConversationExecutor(1),new ChannelRateLimiter(1,java.time.Clock.systemUTC()),List.of());var m=new InboundMessage(ChannelType.FEISHU,"b","t","c","m","o","u","x","text","tr",Instant.now(),Map.of());assertEquals(ChannelRouter.RouteResult.DUPLICATE,router.route(m));}

 @Test void backendGeneratedBindingCodeIsRecognized() {
  assertTrue(ChannelRouter.isBindingCode("de54e771-6e6d-4649-b936-d4cf4b380f78.4hJ_784ZhzUwPKq2neHlKWI3"));
  assertFalse(ChannelRouter.isBindingCode("普通聊天消息"));
  assertFalse(ChannelRouter.isBindingCode("de54e771-6e6d-4649-b936-d4cf4b380f78"));
 }

 @Test void unboundUserBindingCodeCallsConfirm() throws Exception {
  MessageDeduplicator dedup=(c,m)->true; FeishuIdentityClient ids=mock(FeishuIdentityClient.class);
  ConversationExecutor executor=new ConversationExecutor(1); CountDownLatch sent=new CountDownLatch(1);
  List<OutboundMessage> replies=new CopyOnWriteArrayList<>(); ChannelAdapter adapter=new ChannelAdapter(){public ChannelType type(){return ChannelType.FEISHU;}public void send(OutboundMessage m){replies.add(m);sent.countDown();}};
  String code="de54e771-6e6d-4649-b936-d4cf4b380f78.4hJ_784ZhzUwPKq2neHlKWI3";
  when(ids.find("tenant","union","open")).thenReturn(new FeishuIdentityClient.BindingResult(false,null,null,"UNBOUND"));
  when(ids.confirm(code,"tenant","union","open")).thenReturn(new FeishuIdentityClient.BindingResult(true,"12***3","张**","ACTIVE"));
  ChannelRouter router=new ChannelRouter(dedup,mock(ChannelSessionStore.class),ids,mock(AgentGateway.class),executor,new ChannelRateLimiter(10,java.time.Clock.systemUTC()),List.of(adapter));
  var inbound=new InboundMessage(ChannelType.FEISHU,"bot","tenant","chat","binding-message","open","union",code,"text","trace",Instant.now(),Map.of());
  assertEquals(ChannelRouter.RouteResult.ACCEPTED,router.route(inbound)); assertTrue(sent.await(2,TimeUnit.SECONDS));
  verify(ids).confirm(code,"tenant","union","open"); assertTrue(replies.get(0).text().startsWith("绑定成功")); executor.close();
 }
}
