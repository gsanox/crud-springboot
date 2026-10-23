package com.saseformacion.crud.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de POST /api/productos/{id}/ventas: cuántas unidades se venden.
 * Un DTO no tiene por qué parecerse a una entidad: este solo lleva un dato.
 */
public record VentaRequest(

		@NotNull(message = "La cantidad es obligatoria")
		@Positive(message = "La cantidad tiene que ser mayor que 0")
		Integer cantidad) {
}
