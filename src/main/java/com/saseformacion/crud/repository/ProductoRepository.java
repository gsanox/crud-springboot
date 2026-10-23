package com.saseformacion.crud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saseformacion.crud.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

	// Consultas derivadas: Spring Data genera el SQL a partir del nombre del método

	// select count(*) > 0 from productos where upper(nombre) = upper(?)
	boolean existsByNombreIgnoreCase(String nombre);

	// Igual, pero sin contar el propio producto (para cuando se actualiza)
	boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

}
