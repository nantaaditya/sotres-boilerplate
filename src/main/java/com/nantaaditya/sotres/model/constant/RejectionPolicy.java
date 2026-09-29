package com.nantaaditya.sotres.model.constant;

/**
 * How a {@link com.nantaaditya.sotres.configuration.AsyncTaskConfiguration}-managed executor
 * behaves once its pool + queue capacity is exhausted.
 */
public enum RejectionPolicy {
  /** Reject immediately — the submitting thread must handle the rejection. Never blocks. */
  ABORT,
  /** Run the task on the submitting thread. Guarantees the work happens, but can block a
   *  caller that must never block (e.g. the Netty event loop). */
  CALLER_RUNS
}
