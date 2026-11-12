package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.shared.ProjectId;
import java.util.Map;

/** Lo que cargó {@link ArchiveLoader}: el proyecto de cada carpeta y las páginas de cada documento (clave {@code carpeta/fichero}). */
record LoadedArchive(Map<String, ProjectId> projectByFolder, Map<String, Map<Integer, String>> pagesByDocument) {
}
