package io.harness.serializer.spring.converters.executableresponse;

import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.serializer.spring.ProtoWriteConverter;

import com.google.inject.Singleton;
import org.springframework.data.convert.WritingConverter;

@Singleton
@WritingConverter
public class ExecutableResponseWriteConverter extends ProtoWriteConverter<ExecutableResponse> {}
