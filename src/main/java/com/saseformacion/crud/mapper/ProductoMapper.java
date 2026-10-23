package com.saseformacion.crud.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.saseformacion.crud.dto.ProductoRequest;
import com.saseformacion.crud.dto.ProductoResponse;
import com.saseformacion.crud.model.Producto;

/**
 * Convierte entre la entidad Producto y sus DTOs.
 * Es el único sitio que sabe cómo se corresponden los campos: si mañana cambia
 * el JSON o la tabla, solo se toca esta clase.
 * Es un @Component para que Spring lo inyecte en el servicio.
 */
@Component
public class ProductoMapper {

	// DTO de entrada → entidad nueva (sin id ni fecha: los pone el servidor)
	public Producto toEntity(ProductoRequest request) {
		return new Producto(request.nombre(), request.descripcion(), request.precio(), request.stock());
	}

	// Copia los datos del DTO sobre una entidad que ya existe (para actualizar)
	public void updateEntity(Producto producto, ProductoRequest request) {
		producto.setNombre(request.nombre());
		producto.setDescripcion(request.descripcion());
		producto.setPrecio(request.precio());
		producto.setStock(request.stock());
	}

	// Entidad → DTO de salida
	public ProductoResponse toResponse(Producto producto) {
		return new ProductoResponse(
				producto.getId(),
				producto.getNombre(),
				producto.getDescripcion(),
				producto.getPrecio(),
				producto.getStock(),
				producto.getStock() > 0,
				producto.getFechaCreacion());
	}

	public List<ProductoResponse> toResponseList(List<Producto> productos) {
		return productos.stream()
				.map(this::toResponse)
				.toList();
	}

}
