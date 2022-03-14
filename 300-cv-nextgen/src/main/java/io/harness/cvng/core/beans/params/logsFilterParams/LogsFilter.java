/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params.logsFilterParams;

import io.harness.cvng.beans.cvnglog.CVNGLogType;

import io.swagger.annotations.ApiParam;
import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Data
@NoArgsConstructor
public abstract class LogsFilter {
  @ApiParam(required = true) @NotNull @QueryParam("logType") String logType;
  @QueryParam("errorLogsOnly") @ApiParam(defaultValue = "false") boolean errorLogsOnly;

  public CVNGLogType getCVNGLogType() {
    return CVNGLogType.toCVNGLogType(logType);
  }
}
