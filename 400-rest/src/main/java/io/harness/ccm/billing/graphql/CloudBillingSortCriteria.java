package io.harness.ccm.billing.graphql;

import io.harness.ccm.billing.preaggregated.PreAggregateConstants;

import software.wings.graphql.schema.type.aggregation.QLSortOrder;

import com.hazelcast.util.Preconditions;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SqlObject;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloudBillingSortCriteria {
  private CloudSortType sortType;
  private QLSortOrder sortOrder;

  public SqlObject toOrderObject() {
    Preconditions.checkNotNull(sortOrder, "Sort Order is missing column name.");
    if (sortType == null || sortOrder == null) {
      return null;
    }

    String orderIdentifier = null;
    switch (sortType) {
      case Time:
        orderIdentifier = PreAggregateConstants.startTimeTruncatedConstant;
        break;
      case gcpCost:
        orderIdentifier = PreAggregateConstants.entityConstantGcpCost;
        break;
      case gcpProjectId:
        orderIdentifier = PreAggregateConstants.entityConstantGcpProjectId;
        break;
      case gcpProduct:
        orderIdentifier = PreAggregateConstants.entityConstantGcpProduct;
        break;
      case gcpSkuId:
        orderIdentifier = PreAggregateConstants.entityConstantGcpSkuId;
        break;
      case gcpSkuDescription:
        orderIdentifier = PreAggregateConstants.entityConstantGcpSku;
        break;
      case awsBlendedCost:
        orderIdentifier = PreAggregateConstants.entityConstantAwsBlendedCost;
        break;
      case awsUnblendedCost:
        orderIdentifier = PreAggregateConstants.entityConstantAwsUnBlendedCost;
        break;
      case awsService:
        orderIdentifier = PreAggregateConstants.entityConstantAwsService;
        break;
      case awsLinkedAccount:
        orderIdentifier = PreAggregateConstants.entityConstantAwsLinkedAccount;
        break;
      default:
        break;
    }

    OrderObject orderObject = null;
    switch (sortOrder) {
      case ASCENDING:
        orderObject = new OrderObject(OrderObject.Dir.ASCENDING, orderIdentifier);
        break;
      case DESCENDING:
        orderObject = new OrderObject(OrderObject.Dir.DESCENDING, orderIdentifier);
        break;
      default:
        return null;
    }

    return orderObject;
  }
}
