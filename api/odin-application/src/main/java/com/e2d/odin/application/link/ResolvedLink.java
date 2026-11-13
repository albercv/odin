package com.e2d.odin.application.link;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.link.AccessLink;

/** Un enlace válido: la persona reconstruida y el caso o el documento al que lleva, ya comprobados. */
public record ResolvedLink(AccessLink link, Actor actor, Document target) {
}
