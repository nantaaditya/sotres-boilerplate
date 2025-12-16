package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.service.AbstractRetryProcessorService;

public interface RetryProcessorHelper {
  AbstractRetryProcessorService getProcessor(String processType, String processName);
}
