package io.harness.pcf;

public class PcfAppNotFoundException extends PivotalClientApiException {
  public PcfAppNotFoundException(String s) {
    super(s);
  }

  public PcfAppNotFoundException(String s, Throwable t) {
    super(s, t);
  }
}
