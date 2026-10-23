package com.saseformacion.crud.exception;

/**
 * Regla de negocio: no se pueden vender más unidades de las que hay en stock.
 * ManejadorGlobalExcepciones la convierte en un 409 Conflict.
 */
public class StockInsuficienteException extends RuntimeException {

	public StockInsuficienteException(String nombre, int disponible, int pedido) {
		super("Stock insuficiente de '" + nombre + "': hay " + disponible + " y se han pedido " + pedido);
	}

}
