// repository/MultaRepository.java
package com.example.multas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;

@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {
    List<Multa> findByEstudianteId(String estudianteId);
    long countByEstudianteIdAndEstado(String estudianteId, EstadoMulta estado);
}