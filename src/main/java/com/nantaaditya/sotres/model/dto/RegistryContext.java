package com.nantaaditya.sotres.model.dto;

import com.solab.iso8583.IsoMessage;

public record RegistryContext(
    IsoMessage isoMessage,
    boolean lateResponse,
    boolean unknownMatchResponse
) {

}
