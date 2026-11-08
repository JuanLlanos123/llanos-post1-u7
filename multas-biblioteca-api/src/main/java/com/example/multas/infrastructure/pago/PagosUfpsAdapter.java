package com.example.multas.infrastructure.pago;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.Multa;

// infrastructure/pago/PagosUdesAdapter.java
@Component
@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "pagosufps", matchIfMissing = true)
public class PagosUfpsAdapter implements PasarelaPagoPort {

    private final RestTemplate restTemplate;

    @Value("${app.pagos.pagosudes.url:http://localhost:9001/pagosufps/transacciones}")
    private String urlPasarela;

    public PagosUfpsAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    @Override
    public ResultadoPago procesar(Multa multa) {
        try {
            PagosUfpsRequest request = new PagosUfpsRequest(multa.getEstudianteId(), multa.getMonto());
            PagosUfpsResponse response = restTemplate.postForObject(urlPasarela, request, PagosUfpsResponse.class);
            boolean exitoso = response != null && "APROBADA".equals(response.estadoTransaccion());
            String mensaje = response != null && response.mensaje() != null && !response.mensaje().isBlank()
                ? response.mensaje()
                : exitoso ? "Pago aprobado por PagosUDES" : "Transaccion rechazada por PagosUDES";
            return new ResultadoPago("PAGOSUDES", exitoso,
                response != null ? response.idTransaccion() : null,
                mensaje);
        } catch (RestClientException ex) {
            return new ResultadoPago("PAGOSUDES", false, null, "PagosUDES no disponible: " + ex.getMessage());
        }
    }
     private record PagosUfpsRequest(String estudianteId, BigDecimal monto) {}
     private record PagosUfpsResponse(String idTransaccion, String estadoTransaccion, String mensaje) {}
}
