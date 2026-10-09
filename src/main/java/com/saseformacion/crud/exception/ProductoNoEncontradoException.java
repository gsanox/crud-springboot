package com.saseformacion.crud.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Si llega al controlador sin capturar, Spring responde con un 404 Not Found
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductoNoEncontradoException extends RuntimeException {

	public ProductoNoEncontradoException(Long id) {
		super("No existe ningún producto con id " + id);
	}

}
