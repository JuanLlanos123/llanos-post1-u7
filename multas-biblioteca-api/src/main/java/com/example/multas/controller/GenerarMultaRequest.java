package com.example.multas.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GenerarMultaRequest(
    @NotBlank String estudianteId,
    @NotBlank String concepto,
    @Min(1) int diasAtraso
) {}