package com.e2d.odin.application.shared;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** Las horas en que se etiqueta, en la zona de la instalación. Una ventana como 22:00-07:00 cruza la medianoche. */
public record TaggingWindow(LocalTime from, LocalTime to, boolean always, ZoneId zone) {

    public static TaggingWindow always(ZoneId zone) {
        return new TaggingWindow(LocalTime.MIN, LocalTime.MIN, true, zone);
    }

    public static TaggingWindow between(LocalTime from, LocalTime to, ZoneId zone) {
        return new TaggingWindow(from, to, false, zone);
    }

    public boolean isOpen(Instant now) {
        if (always) {
            return true;
        }
        LocalTime time = now.atZone(zone).toLocalTime();
        return from.isBefore(to) ? !time.isBefore(from) && time.isBefore(to) : !time.isBefore(from) || time.isBefore(to);
    }
}
