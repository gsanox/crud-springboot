package com.saseformacion.crud.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos que el servidor DEVUELVE al cliente.
 * No tiene por qué ser igual que la entidad: aquí añadimos "disponible",
 * un campo calculado que no existe en la tabla.
 */
public record ProductoResponse(
		Long id,
		String nombre,
		String descripcion,
		BigDecimal precio,
		Integer stock,
		boolean disponible,
		LocalDateTime fechaCreacion) {
}
