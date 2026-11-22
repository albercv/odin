package com.e2d.odin.domain.citation;

/** Lo detectado no vale hasta que una persona lo confirma (D-050); un descartado no vuelve a salir. */
public enum LinkStatus {
    DETECTED, CONFIRMED, DISMISSED
}
