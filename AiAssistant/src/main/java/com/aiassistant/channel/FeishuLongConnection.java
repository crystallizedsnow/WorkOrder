package com.aiassistant.channel;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
@Component
@ConditionalOnProperty(name="workorder.channel.feishu.enabled",havingValue="true")
public class FeishuLongConnection {
 private final FeishuProperties p;private final FeishuMessageParser parser;private final ChannelRouter router;private final FeishuCardActionHandler cards;private final AtomicReference<State> state=new AtomicReference<>(State.STARTING);private volatile Instant lastEventAt;
 public FeishuLongConnection(FeishuProperties p,FeishuMessageParser parser,ChannelRouter router,FeishuCardActionHandler cards){this.p=p;this.parser=parser;this.router=router;this.cards=cards;}
 @PostConstruct void start(){if(p.getAppId()==null||p.getAppId().isBlank()||p.getAppSecret()==null||p.getAppSecret().isBlank())throw new IllegalStateException("Feishu credentials required");var dispatcher=EventDispatcher.newBuilder("","").onP2CardActionTrigger(cards).onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler(){@Override public void handle(P2MessageReceiveV1 event){lastEventAt=Instant.now();if(event.getEvent()!=null&&event.getEvent().getMessage()!=null&&"p2p".equals(event.getEvent().getMessage().getChatType()))router.route(parser.parse(event));}}).build();Thread t=new Thread(()->{try{state.set(State.CONNECTING);new Client.Builder(p.getAppId(),p.getAppSecret()).eventHandler(dispatcher).build().start();state.set(State.CONNECTED);}catch(Exception e){state.set(State.FAILED);}},"feishu-long-connection");t.setDaemon(true);t.start();}
 public State state(){return state.get();}public Instant lastEventAt(){return lastEventAt;}public enum State{STARTING,CONNECTING,CONNECTED,FAILED}
}
