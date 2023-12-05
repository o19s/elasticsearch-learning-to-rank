package com.o19s.es.ltr.feature.store;

import java.util.Map;
import java.util.function.Supplier;

public class ExtraLoggingSupplier implements Supplier<Map<String, Object>> {
  protected Supplier<Map<String, Object>> supplier;

  public void setSupplier(Supplier<Map<String, Object>> supplier) {
    this.supplier = supplier;
  }

  /**
   * Return a Map to add additional information to be returned when logging feature values.
   *
   * <p>This Map will only be non-null during the LoggingFetchSubPhase.
   */
  @Override
  public Map<String, Object> get() {
    if (supplier != null) {
      return supplier.get();
    }
    return null;
  }
}
