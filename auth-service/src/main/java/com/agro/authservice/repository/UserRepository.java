package com.agro.authservice.repository;

import com.agro.authservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from User u
            where (:q is null
                   or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.full_name) like lower(concat('%', :q, '%')))
              and (:roleId is null or u.role_id = :roleId)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("q") String q,
                      @Param("roleId") Integer roleId,
                      @Param("status") String status,
                      Pageable pageable);
}
