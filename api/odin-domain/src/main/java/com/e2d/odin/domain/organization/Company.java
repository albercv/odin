package com.e2d.odin.domain.organization;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Set;
import java.util.regex.Pattern;

/** {@code codeReviewed} es falso en las empresas migradas: su código salió de las iniciales y falta revisarlo. */
public record Company(CompanyId id, String name, String code, Set<Capability> capabilities, boolean codeReviewed,
        ProjectLabel projectLabel) {

    private static final Pattern VALID_CODE = Pattern.compile("[A-Z0-9]{2,6}");

    public Company(CompanyId id, String name, String code, Set<Capability> capabilities) {
        this(id, name, code, capabilities, true, ProjectLabel.DEFAULT);
    }

    public Company(CompanyId id, String name, String code, Set<Capability> capabilities, boolean codeReviewed) {
        this(id, name, code, capabilities, codeReviewed, ProjectLabel.DEFAULT);
    }

    public Company {
        if (code == null || !VALID_CODE.matcher(code).matches()) {
            throw new ValidationException("El código de la empresa tiene de 2 a 6 letras mayúsculas o cifras");
        }
        capabilities = Set.copyOf(capabilities);
    }

    public Company withName(String newName) {
        return new Company(id, newName, code, capabilities, codeReviewed, projectLabel);
    }

    public Company withCode(String newCode) {
        return new Company(id, name, newCode, capabilities, codeReviewed, projectLabel);
    }

    public Company withCapabilities(Set<Capability> newCapabilities) {
        return new Company(id, name, code, newCapabilities, codeReviewed, projectLabel);
    }

    public Company withProjectLabel(ProjectLabel newLabel) {
        return new Company(id, name, code, capabilities, codeReviewed, newLabel);
    }

    public Company withCodeReviewed(boolean reviewed) {
        return new Company(id, name, code, capabilities, reviewed, projectLabel);
    }
}
