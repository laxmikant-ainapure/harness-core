package software.wings.rules;

import io.harness.queue.QueueListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by peeyushaggarwal on 5/11/16.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD, ElementType.TYPE})
public @interface Listeners {
  /**
   * Value.
   *
   * @return the class<? extends abstract queue listener>[]
   */
  Class<? extends QueueListener>[] value() default {};
}
