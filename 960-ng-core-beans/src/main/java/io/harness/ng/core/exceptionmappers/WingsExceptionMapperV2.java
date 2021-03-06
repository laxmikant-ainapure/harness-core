package io.harness.ng.core.exceptionmappers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.ReportTarget.REST_API;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException;
import io.harness.logging.ExceptionLogger;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import java.util.List;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WingsExceptionMapperV2 implements ExceptionMapper<WingsException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(WingsException exception) {
    ExceptionLogger.logProcessedMessages(exception, MANAGER, log);

    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(exception, REST_API);
    ErrorCode errorCode = exception.getCode() != null ? exception.getCode() : ErrorCode.UNKNOWN_ERROR;
    ErrorDTO errorBody = ErrorDTO.newError(Status.ERROR, errorCode, null);

    if (!responseMessages.isEmpty()) {
      errorBody.setMessage(responseMessages.get(0).getMessage());
    }

    return Response.status(resolveHttpStatus(responseMessages)).entity(errorBody).build();
  }

  private Response.Status resolveHttpStatus(List<ResponseMessage> responseMessageList) {
    ErrorCode errorCode = null;
    if (isNotEmpty(responseMessageList)) {
      errorCode = responseMessageList.get(responseMessageList.size() - 1).getCode();
    }
    if (errorCode != null) {
      return Response.Status.fromStatusCode(errorCode.getStatus().getCode());
    } else {
      return INTERNAL_SERVER_ERROR;
    }
  }
}
