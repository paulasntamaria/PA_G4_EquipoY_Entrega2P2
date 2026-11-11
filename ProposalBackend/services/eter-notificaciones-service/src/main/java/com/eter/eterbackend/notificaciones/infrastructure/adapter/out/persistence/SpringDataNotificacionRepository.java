package com.eter.eterbackend.notificaciones.infrastructure.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificacionRepository extends JpaRepository<NotificacionJpaEntity, UUID> {

	boolean existsByEventIdAndDestinatarioEmail(UUID eventId, String destinatarioEmail);

	@Query("""
			SELECT n FROM NotificacionJpaEntity n
			WHERE n.nitUniv = :nitUniv
			  AND LOWER(n.destinatarioEmail) = LOWER(:email)
			  AND (:soloNoLeidas = false OR n.leida = false)
			ORDER BY n.creadaEn DESC
			""")
	List<NotificacionJpaEntity> buscarBandeja(@Param("nitUniv") String nitUniv,
			@Param("email") String email,
			@Param("soloNoLeidas") boolean soloNoLeidas,
			Pageable pageable);

	@Query("""
			SELECT COUNT(n) FROM NotificacionJpaEntity n
			WHERE n.nitUniv = :nitUniv
			  AND LOWER(n.destinatarioEmail) = LOWER(:email)
			  AND (:soloNoLeidas = false OR n.leida = false)
			""")
	long contarTotal(@Param("nitUniv") String nitUniv,
			@Param("email") String email,
			@Param("soloNoLeidas") boolean soloNoLeidas);

	@Query("""
			SELECT COUNT(n) FROM NotificacionJpaEntity n
			WHERE n.nitUniv = :nitUniv
			  AND LOWER(n.destinatarioEmail) = LOWER(:email)
			  AND n.leida = false
			""")
	long contarNoLeidas(@Param("nitUniv") String nitUniv, @Param("email") String email);

	@Modifying
	@Query("""
			UPDATE NotificacionJpaEntity n
			SET n.leida = true, n.leidaEn = :cuando
			WHERE n.nitUniv = :nitUniv
			  AND LOWER(n.destinatarioEmail) = LOWER(:email)
			  AND n.leida = false
			""")
	int marcarTodasLeidas(@Param("nitUniv") String nitUniv,
			@Param("email") String email,
			@Param("cuando") OffsetDateTime cuando);
}
