package com.example.multas.model;
// model/MultaNotFoundException.java
public class MultaNotFoundException extends RuntimeException {
    public MultaNotFoundException(String mensaje) { super(mensaje); }
}