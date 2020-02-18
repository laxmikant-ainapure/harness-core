package software.wings.delegatetasks.validation;

import java.util.List;

/**
 * Created by brett on 11/1/17
 */
public interface DelegateValidateTask {
  List<DelegateConnectionResult> validationResults();
  List<String> getCriteria();
}
