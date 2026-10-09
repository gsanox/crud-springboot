package com.saseformacion.crud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saseformacion.crud.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

}
