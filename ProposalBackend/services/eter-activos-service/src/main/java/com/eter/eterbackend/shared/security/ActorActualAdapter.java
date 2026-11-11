package com.eter.eterbackend.shared.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.activos.application.port.out.ActorActualPort;

/** Lee el usuario del JWT que el filtro dejo en el contexto de seguridad. */
@Component
public class ActorActualAdapter implements ActorActualPort {

	@Override
	public String email() {
		var autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
				&& usuario.email() != null && !usuario.email().isBlank()) {
			return usuario.email();
		}
		return "sistema";
	}
}
