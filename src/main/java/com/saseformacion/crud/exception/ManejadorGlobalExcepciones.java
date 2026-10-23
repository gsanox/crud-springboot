package com.saseformacion.crud.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.saseformacion.crud.dto.RespuestaError;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Atrapa las excepciones que salen de CUALQUIER controlador y las convierte en una
 * respuesta HTTP con el código adecuado y un JSON con el formato de RespuestaError.
 *
 * Así ni el servicio ni el controlador tienen que poner try/catch: el servicio lanza
 * una excepción con significado ("no existe", "duplicado"...) y aquí se decide qué
 * código HTTP le corresponde.
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

	private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

	// 404: el recurso pedido no existe
	@ExceptionHandler(ProductoNoEncontradoException.class)
	public ResponseEntity<RespuestaError> manejarNoEncontrado(ProductoNoEncontradoException ex, HttpServletRequest request) {
		return crearRespuesta(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
	}

	// 409: la petición es correcta, pero choca con el estado actual de los datos.
	// Un mismo método puede atender varias excepciones.
	@ExceptionHandler({ ProductoDuplicadoException.class, StockInsuficienteException.class })
	public ResponseEntity<RespuestaError> manejarConflicto(RuntimeException ex, HttpServletRequest request) {
		return crearRespuesta(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of());
	}

	// 400: algún campo del DTO no cumple sus anotaciones (@NotBlank, @Positive...).
	// La lanza Spring cuando falla un @Valid del controlador.
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<RespuestaError> manejarValidacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> errores = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errores.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return crearRespuesta(HttpStatus.BAD_REQUEST, "Hay campos con valores no válidos", request, errores);
	}

	// 400: el cuerpo no es un JSON válido o un campo tiene un tipo incorrecto ("precio": "caro")
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<RespuestaError> manejarJsonIncorrecto(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return crearRespuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o algún campo tiene un tipo incorrecto", request, Map.of());
	}

	// 400: un parámetro de la URL no tiene el tipo esperado (/api/productos/abc)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RespuestaError> manejarTipoIncorrecto(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		String mensaje = "El valor '" + ex.getValue() + "' no es válido para el parámetro '" + ex.getName() + "'";
		return crearRespuesta(HttpStatus.BAD_REQUEST, mensaje, request, Map.of());
	}

	// Red de seguridad: cualquier otra excepción
	@ExceptionHandler(Exception.class)
	public ResponseEntity<RespuestaError> manejarErrorInesperado(Exception ex, HttpServletRequest request) {
		// Las excepciones propias de Spring MVC ya saben su código HTTP
		// (URL inexistente → 404, verbo no permitido → 405...): lo respetamos
		if (ex instanceof ErrorResponse errorDeSpring) {
			HttpStatus estado = HttpStatus.valueOf(errorDeSpring.getStatusCode().value());
			return crearRespuesta(estado, ex.getMessage(), request, Map.of());
		}

		// Un error de verdad: se guarda entero en el log para el programador,
		// pero al cliente no se le dan detalles internos (trazas, SQL...)
		log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
		return crearRespuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado", request, Map.of());
	}

	private ResponseEntity<RespuestaError> crearRespuesta(HttpStatus estado, String mensaje, HttpServletRequest request,
			Map<String, String> errores) {
		RespuestaError cuerpo = new RespuestaError(
				LocalDateTime.now(),
				estado.value(),
				estado.getReasonPhrase(),
				mensaje,
				request.getRequestURI(),
				errores);
		return ResponseEntity.status(estado).body(cuerpo);
	}

}
