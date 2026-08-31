package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.entity.EventLog;

public interface EventLogService {

  /** Persist an audit row off the request thread (fire-and-forget; a DB failure is logged only). */
  void save(EventLog eventLog);

  void remove(int days);
}
