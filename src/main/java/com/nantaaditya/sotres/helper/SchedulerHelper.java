package com.nantaaditya.sotres.helper;

import reactor.core.scheduler.Scheduler;

public interface SchedulerHelper {
  Scheduler from(String name);
}