-- Tipo de documento calculado al clasificar la primera carga: solo para proponer y enseñarlo en la bandeja (no es un
-- campo de Odín). Sin CHECK de valores: las reglas de tipo pueden crecer.
ALTER TABLE document ADD COLUMN intake_type text NULL;
