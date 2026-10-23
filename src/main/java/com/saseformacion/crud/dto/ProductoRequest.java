package com.saseformacion.crud.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos que el cliente ENVÍA para crear o actualizar un producto.
 * No lleva id ni fechaCreacion: esos los decide el servidor, no el cliente.
 * Las anotaciones de validación se comprueban en el controlador gracias a @Valid.
 */
public record ProductoRequest(

		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
		String nombre,

		@Size(max = 500, message = "La descripción no puede tener más de 500 caracteres")
		String descripcion,

		@NotNull(message = "El precio es obligatorio")
		@Positive(message = "El precio tiene que ser mayor que 0")
		@Digits(integer = 8, fraction = 2, message = "El precio admite como mucho 8 cifras enteras y 2 decimales")
		BigDecimal precio,

		@NotNull(message = "El stock es obligatorio")
		@PositiveOrZero(message = "El stock no puede ser negativo")
		Integer stock) {
}
