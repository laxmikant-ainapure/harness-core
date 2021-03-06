package io.harness.eventsframework.impl.redis;

import io.harness.eventsframework.api.ConsumerShutdownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.redis.RedisConfig;

import java.time.Duration;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisSerialConsumer extends RedisAbstractConsumer {
  public RedisSerialConsumer(
      String topicName, String groupName, String consumerName, RedisConfig redisConfig, Duration maxProcessingTime) {
    super(topicName, groupName, consumerName, redisConfig, maxProcessingTime, 1);
  }

  @Override
  public List<Message> read(Duration maxWaitTime) throws ConsumerShutdownException {
    return getMessages(true, maxWaitTime);
  }

  public static RedisSerialConsumer of(String topicName, String groupName, String consumerName,
      @NotNull RedisConfig redisConfig, Duration maxProcessingTime) {
    return new RedisSerialConsumer(topicName, groupName, consumerName, redisConfig, maxProcessingTime);
  }
}
