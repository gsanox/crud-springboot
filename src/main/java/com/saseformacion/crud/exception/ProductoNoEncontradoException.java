package com.saseformacion.crud.exception;

/**
 * El producto pedido no existe. ManejadorGlobalExcepciones la convierte en un 404.
 * Hereda de RuntimeException (excepción no comprobada): no obliga a poner throws
 * ni try/catch en cada método por el que pasa.
 */
public class ProductoNoEncontradoException extends RuntimeException {

	public ProductoNoEncontradoException(Long id) {
		super("No existe ningún producto con id " + id);
	}

}
