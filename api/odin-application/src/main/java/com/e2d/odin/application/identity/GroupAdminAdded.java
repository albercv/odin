package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.User;

/** {@code keptPassword}: era una persona que ya existía con contraseña; se queda con ella. */
public record GroupAdminAdded(User admin, boolean alreadyExisted, boolean keptPassword) {
}
