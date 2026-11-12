package com.e2d.odin.application.shared;

import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;

public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static void requireGroupAdmin(Actor actor) {
        if (!actor.isGroupAdmin()) {
            throw new ForbiddenException("Solo el administrador del grupo puede hacer esto");
        }
    }

    public static void requireAdminOf(Actor actor, CompanyId company) {
        if (!actor.administers(company)) {
            throw new ForbiddenException("No tienes permiso sobre esta empresa");
        }
    }

    public static void requireAdmin(Actor actor) {
        if (actor.role() == Role.WORKER) {
            throw new ForbiddenException("Solo un administrador puede hacer esto");
        }
    }

    public static void requireWorker(Actor actor) {
        if (actor.role() != Role.WORKER) {
            throw new ForbiddenException("Solo un trabajador puede hacer esto");
        }
    }

    public static void requireCapability(Company company, Capability capability) {
        if (!company.capabilities().contains(capability)) {
            throw new ForbiddenException("Tu empresa no tiene activada esta función");
        }
    }
}
