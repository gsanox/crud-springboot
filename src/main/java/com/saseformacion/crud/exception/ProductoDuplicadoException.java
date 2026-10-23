package com.saseformacion.crud.exception;

/**
 * Regla de negocio: no puede haber dos productos con el mismo nombre.
 * ManejadorGlobalExcepciones la convierte en un 409 Conflict.
 */
public class ProductoDuplicadoException extends RuntimeException {

	public ProductoDuplicadoException(String nombre) {
		super("Ya existe un producto con el nombre '" + nombre + "'");
	}

}
