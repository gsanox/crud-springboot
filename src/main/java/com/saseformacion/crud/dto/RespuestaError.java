package com.saseformacion.crud.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cuerpo JSON de TODAS las respuestas de error de la API.
 * Lo construye ManejadorGlobalExcepciones, así el cliente siempre recibe el mismo formato.
 *
 * @param errores errores de validación campo a campo (vacío si el error no es de validación)
 */
public record RespuestaError(
		LocalDateTime fecha,
		int estado,
		String error,
		String mensaje,
		String ruta,
		Map<String, String> errores) {
}
