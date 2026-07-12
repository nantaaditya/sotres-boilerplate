package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum TemplateGroup {
  CLIENT_SPEC_REQUEST("client_spec_request", "request"),
  CLIENT_SPEC_RESPONSE("client_spec_response", "response");

  private String group;
  private String propertyId;

  TemplateGroup(String group, String propertyId) {
    this.group = group;
    this.propertyId = propertyId;
  }
}
