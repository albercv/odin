-- Fase 2a: nombre y apellidos de la persona, código de persona y código de empresa (D-040).
ALTER TABLE app_user ADD COLUMN first_name text;
ALTER TABLE app_user ADD COLUMN last_name text;
ALTER TABLE app_user ADD COLUMN person_code text;

-- Las personas migradas conservan su nombre completo como nombre; los apellidos se rellenan a mano.
UPDATE app_user SET first_name = display_name;
ALTER TABLE app_user DROP COLUMN display_name;
ALTER TABLE app_user ALTER COLUMN first_name SET NOT NULL;

-- Solo para las filas que ya existen. Los códigos nuevos los genera la aplicación con SecureRandom.
DO $$
DECLARE
    person record;
    candidate text;
    alphabet constant text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    byte int;
BEGIN
    FOR person IN SELECT id FROM app_user ORDER BY seq LOOP
        LOOP
            candidate := '';
            WHILE length(candidate) < 4 LOOP
                -- Bytes de gen_random_uuid() (criptográfico), sin los 6 y 8 que llevan los bits fijos de la versión. Se
                -- descartan los valores >= 248 (mayor múltiplo de 31 hasta 256) para que el resto no sesgue el alfabeto.
                FOR byte IN SELECT get_byte(uuid_send(gen_random_uuid()), n) FROM generate_series(0, 15) AS n
                             WHERE n NOT IN (6, 8) LOOP
                    IF byte < 248 AND length(candidate) < 4 THEN
                        candidate := candidate || substr(alphabet, 1 + byte % 31, 1);
                    END IF;
                END LOOP;
            END LOOP;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM app_user WHERE person_code = candidate);
        END LOOP;
        UPDATE app_user SET person_code = candidate WHERE id = person.id;
    END LOOP;
END
$$;

ALTER TABLE app_user ALTER COLUMN person_code SET NOT NULL;
-- Sobre todas las filas, borradas incluidas: un código no se reutiliza nunca.
CREATE UNIQUE INDEX app_user_person_code_key ON app_user (person_code);

ALTER TABLE company ADD COLUMN code text;

-- Iniciales de las palabras del nombre, sin tildes y en mayúsculas, hasta 4; si salen menos de 2, las 3 primeras
-- letras del nombre; si se repite, una cifra al final.
DO $$
DECLARE
    item record;
    plain text;
    base text;
    candidate text;
    suffix int;
BEGIN
    FOR item IN SELECT id, name FROM company ORDER BY seq LOOP
        plain := upper(translate(item.name, 'áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ',
                                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'));
        SELECT coalesce(string_agg(left(word, 1), ''), '') INTO base
        FROM (SELECT word FROM regexp_split_to_table(regexp_replace(plain, '[^A-Z0-9]+', ' ', 'g'), ' ') AS word
              WHERE word <> '' LIMIT 4) AS words;
        IF length(base) < 2 THEN
            base := left(regexp_replace(plain, '[^A-Z0-9]', '', 'g'), 3);
        END IF;
        IF length(base) < 2 THEN
            base := rpad(base, 2, 'X');
        END IF;
        candidate := base;
        suffix := 1;
        WHILE EXISTS (SELECT 1 FROM company WHERE code = candidate) LOOP
            suffix := suffix + 1;
            candidate := base || suffix;
        END LOOP;
        UPDATE company SET code = candidate WHERE id = item.id;
    END LOOP;
END
$$;

ALTER TABLE company ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX company_code_key ON company (code);
