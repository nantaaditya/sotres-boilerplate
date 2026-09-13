package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

@Log4j2
public class AsyncMDCTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } catch (Throwable e) {
                log.error(AppLogMessage.message("error in async task {}, {}", e.getMessage()).error(e));
            } finally {
                MDC.clear();
            }
        };
    }
}