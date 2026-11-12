package com.e2d.odin.adapter.web.security;

import com.e2d.odin.application.usage.WorkerTool;
import java.util.List;
import java.util.Optional;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Las rutas del trabajador (clave de acceso) y la herramienta que cuenta cada una en el volumen entregado. */
public final class WorkerRoutes {

    private record Route(String method, PathPattern pattern, WorkerTool tool) {
    }

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final List<Route> ROUTES = List.of(
            route("GET", "/v1/me", WorkerTool.DESCRIBE_ME),
            route("POST", "/v1/search", WorkerTool.SEARCH),
            route("POST", "/v1/similar-cases", WorkerTool.SIMILAR_CASES),
            route("GET", "/v1/documents/{documentId}/pages/{pageNumber}", WorkerTool.READ_PAGE),
            route("GET", "/v1/documents", WorkerTool.LIST_DOCUMENTS),
            route("POST", "/v1/citations/verify", WorkerTool.VERIFY_CITATION),
            route("POST", "/v1/cards", WorkerTool.SAVE_CARD),
            route("GET", "/v1/me/cards", WorkerTool.MY_CARDS),
            route("GET", "/v1/me/questions", WorkerTool.MY_QUESTIONS));

    /** Los dos primeros segmentos de cada ruta: una sola lista, así prefijos y rutas no pueden divergir. */
    private static final List<String> PREFIXES = ROUTES.stream()
            .map(r -> r.pattern().getPatternString().split("/")).map(parts -> "/" + parts[1] + "/" + parts[2])
            .distinct().toList();

    private WorkerRoutes() {
    }

    private static Route route(String method, String pattern, WorkerTool tool) {
        return new Route(method, PARSER.parse(pattern), tool);
    }

    public static boolean isWorkerPath(String path) {
        return PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    public static Optional<WorkerTool> toolFor(String method, String path) {
        var parsed = PathContainer.parsePath(path);
        return ROUTES.stream().filter(r -> r.method().equals(method) && r.pattern().matches(parsed))
                .map(Route::tool).findFirst();
    }
}
