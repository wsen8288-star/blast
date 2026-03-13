package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    User findByUsername(String username);
    User findByEmail(String email);
    long countByRoles_Id(Long roleId);
    boolean existsByIdAndRoles_RoleCodeIgnoreCase(Long id, String roleCode);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("select distinct u from User u left join fetch u.roles")
    List<User> findAllWithRoles();
}
