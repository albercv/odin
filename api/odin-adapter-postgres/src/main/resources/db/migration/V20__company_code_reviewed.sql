-- Administración del grupo (B-006): el código de las empresas que ya existían salió de sus iniciales, así que
-- queda por revisar. Las empresas nuevas nacen revisadas (DEFAULT true).
ALTER TABLE company ADD COLUMN code_reviewed boolean NOT NULL DEFAULT true;
UPDATE company SET code_reviewed = false;
