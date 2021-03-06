package io.harness.batch.processing.reader;

import io.harness.event.grpc.PublishedMessage;
import io.harness.event.grpc.PublishedMessage.PublishedMessageKeys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.data.MongoItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Qualifier("mongoEventReader")
public class MongoEventReaderFactory implements EventReaderFactory {
  private static final int READER_BATCH_SIZE = 500;
  @Autowired private MongoTemplate mongoTemplate;

  @Override
  public ItemReader<PublishedMessage> getEventReader(
      String accountId, String messageType, Long startDate, Long endDate, int batchSize) {
    Query query = new Query();
    query.addCriteria(new Criteria().andOperator(Criteria.where(PublishedMessageKeys.accountId).is(accountId),
        Criteria.where(PublishedMessageKeys.type).is(messageType),
        Criteria.where(PublishedMessageKeys.createdAt).gte(startDate).lt(endDate)));

    query.with(Sort.by(PublishedMessageKeys.accountId, PublishedMessageKeys.type, PublishedMessageKeys.createdAt,
        PublishedMessageKeys.occurredAt));

    MongoItemReader<PublishedMessage> reader = new MongoItemReader<>();
    reader.setTemplate(mongoTemplate);
    reader.setCollection("publishedMessages");
    reader.setTargetType(PublishedMessage.class);
    reader.setQuery(query);
    reader.setPageSize(batchSize);
    return reader;
  }

  @Override
  public ItemReader<PublishedMessage> getEventReader(
      String accountId, String messageType, Long startDate, Long endDate) {
    return getEventReader(accountId, messageType, startDate, endDate, READER_BATCH_SIZE);
  }
}
