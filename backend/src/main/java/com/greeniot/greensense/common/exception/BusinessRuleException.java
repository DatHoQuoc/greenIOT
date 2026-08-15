package com.greeniot.greensense.common.exception;

import lombok.Getter;

/**
 * A request that is well-formed but conflicts with the domain state — pump still in
 * cooldown, e-mail already registered, actuator locked to MANUAL.
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
