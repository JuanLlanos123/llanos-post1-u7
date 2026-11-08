package com.example.multas.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.multas.domain.PagoRechazadoException;
import com.example.multas.service.MultaService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PagoRechazadoHttpTest {

    @Test
    void rejectedPaymentReturns402AndProviderMessage() throws Exception {
        String mensajeProveedor = "Fondos insuficientes";
        MultaService multaService = mock(MultaService.class);
        when(multaService.pagarConPasarela(1L)).thenThrow(new PagoRechazadoException(mensajeProveedor));
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MultaController(multaService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mockMvc.perform(post("/api/multas/1/pagar-en-linea"))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error").value(mensajeProveedor));
    }
}
