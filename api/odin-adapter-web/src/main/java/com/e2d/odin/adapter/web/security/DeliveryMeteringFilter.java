package com.e2d.odin.adapter.web.security;

import com.e2d.odin.application.port.DeliveryMeter;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.identity.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cuenta los bytes de cada respuesta 200 de las rutas del trabajador y los apunta por empresa y herramienta, nunca por
 * persona. Cuenta sin copiar el cuerpo; un fallo al apuntar se escribe en el registro y no rompe la respuesta.
 */
@Component
@Order(3)
public class DeliveryMeteringFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryMeteringFilter.class);

    private final DeliveryMeter meter;
    private final Clock clock;

    public DeliveryMeteringFilter(DeliveryMeter meter, Clock clock) {
        this.meter = meter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<WorkerTool> tool = WorkerRoutes.toolFor(request.getMethod(), request.getRequestURI());
        if (tool.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        CountingResponse counting = new CountingResponse(response);
        chain.doFilter(request, counting);
        counting.flushWriter();
        if (response.getStatus() == HttpServletResponse.SC_OK && request.getAttribute(CurrentActor.CHAT_ACCESS) == null
                && request.getAttribute(CurrentActor.ATTRIBUTE) instanceof Actor actor) {
            record(actor, tool.get(), counting.bytes());
        }
    }

    private void record(Actor actor, WorkerTool tool, long bytes) {
        try {
            meter.record(actor.requireCompany(), tool, bytes, clock.instant());
        } catch (RuntimeException e) {
            LOG.warn("No se pudo apuntar el volumen entregado de {}", tool, e);
        }
    }

    /** Deja pasar la salida tal cual y suma los bytes que escribe quien responde. */
    private static final class CountingResponse extends HttpServletResponseWrapper {

        private CountingStream stream;
        private PrintWriter writer;

        CountingResponse(HttpServletResponse response) {
            super(response);
        }

        long bytes() {
            return stream == null ? 0 : stream.count;
        }

        void flushWriter() {
            if (writer != null) {
                writer.flush();
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getOutputStream());
            }
            return stream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), getCharacterEncoding()));
            }
            return writer;
        }
    }

    private static final class CountingStream extends ServletOutputStream {

        private final ServletOutputStream delegate;
        private long count;

        CountingStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }
    }
}
