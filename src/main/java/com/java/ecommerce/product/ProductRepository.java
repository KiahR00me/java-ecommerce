package com.java.ecommerce.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrue();

    List<Product> findByCategoryId(Long categoryId);

    @Query("""
            select p
            from Product p
            join p.category c
            where (:categoryId is null or c.id = :categoryId)
                and (:active is null or p.active = :active)
                and (:search is null
                    or lower(p.name) like lower(concat('%', :search, '%'))
                    or lower(coalesce(p.description, '')) like lower(concat('%', :search, '%'))
                    or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<Product> search(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("""
            select max(p.id)
            from Product p
            """)
    Optional<Long> findMaxId();

    @Query("""
            select
                count(p) as totalCount,
                sum(case when p.active = true then 1 else 0 end) as activeCount,
                sum(case when p.active = false then 1 else 0 end) as inactiveCount
            from Product p
            join p.category c
            where (:categoryId is null or c.id = :categoryId)
              and (:search is null
                or lower(p.name) like lower(concat('%', :search, '%'))
                or lower(coalesce(p.description, '')) like lower(concat('%', :search, '%'))
                or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    ProductCountProjection countByFilter(
            @Param("search") String search,
            @Param("categoryId") Long categoryId);
}
