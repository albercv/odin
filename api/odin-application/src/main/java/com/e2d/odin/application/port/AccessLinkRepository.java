package com.e2d.odin.application.port;

import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

/** Los enlaces de corta duración. No se listan: el token es la única forma de llegar a uno. */
public interface AccessLinkRepository {

    void save(AccessLink link, String tokenHash);

    /** El único acceso sin empresa: quien llega con un token no sabe a qué empresa pertenece. */
    Optional<AccessLink> findByTokenHash(String tokenHash);

    /** Ata el enlace al primer equipo que lo usa y devuelve la dirección a la que quedó atado; vacío si el enlace ya no existe. */
    Optional<String> bind(AccessLink link, String address);

    /** Reserva un fichero del tope de subidas, de forma atómica; false si ya no cabe. */
    boolean reserveUpload(AccessLink link, int max);

    /** Anula los enlaces de una persona en una empresa (al revocar su clave). */
    void deleteOf(CompanyId companyId, UserId userId);
}
