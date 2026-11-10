package com.starkinhon.soulsjjk.compat;

public final class RegisterPayloadHandlersEvent {
    public PayloadRegistrar registrar(String version) {
        return new PayloadRegistrar();
    }
}
