package software.wings.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.dl.GridFsDbFileExt;
import software.wings.service.intfc.ExecutionLogs;

import javax.inject.Inject;

// TODO: Auto-generated Javadoc

/**
 * Created by anubhaw on 2/17/16.
 */
public class ExecutionLogsImpl implements ExecutionLogs {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject private GridFsDbFileExt gridFSDBFileExt;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ExecutionLogs#appendLogs(java.lang.String, java.lang.String)
   */
  public void appendLogs(String executionId, String logs) {
    logger.info("Saving log for execution ID: " + executionId);
    gridFSDBFileExt.appendToFile(executionId, logs);
    logger.info("Saved following log text in GridFS: " + logs);
  }
}
