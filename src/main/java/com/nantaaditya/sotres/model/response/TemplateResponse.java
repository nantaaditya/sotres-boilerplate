package com.nantaaditya.sotres.model.response;

import com.nantaaditya.sotres.entity.SystemProperties;

public record TemplateResponse(long id, String group, String selector, String template) {

  public static TemplateResponse from(SystemProperties sp) {
    return new TemplateResponse(sp.getId(), sp.getGroupId(), sp.getPropertyId(), sp.getPropertyValue());
  }
}
