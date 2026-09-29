package com.nantaaditya.sotres.model.constant;

import java.util.Set;

public interface IsoCallbackConstant {

  String CALLBACK_NAME = "iso-callback-message";
  String CALLBACK_ATTRIBUTE = "iso-callback-message-category";
  Set<Integer> MTIs = Set.of(0x800, 0x810);
}
