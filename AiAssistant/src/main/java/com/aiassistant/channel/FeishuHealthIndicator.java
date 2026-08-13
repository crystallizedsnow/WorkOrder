package com.aiassistant.channel;
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component("feishuChannel")
@ConditionalOnProperty(name="workorder.channel.feishu.enabled",havingValue="true")
public class FeishuHealthIndicator implements HealthIndicator {
 private final FeishuLongConnection c;public FeishuHealthIndicator(FeishuLongConnection c){this.c=c;}
 public Health health(){Health.Builder b=c.state()==FeishuLongConnection.State.FAILED?Health.down():Health.up();b.withDetail("state",c.state().name());if(c.lastEventAt()!=null)b.withDetail("lastEventAt",c.lastEventAt());return b.build();}
}
