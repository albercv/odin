package com.e2d.odin.application.identity;

import java.util.Optional;

public record NewGroupAdmin(String firstName, String lastName, String email, Optional<String> password) {
}
