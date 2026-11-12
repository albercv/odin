package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonCodesTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();

    private void personWithCode(String code) {
        users.save(new User(new UserId(UUID.randomUUID()), "Wanda", Optional.empty(), code, Optional.empty(), false,
                true, Optional.empty()));
    }

    @Test
    void collisionGeneratesAnotherCode() {
        personWithCode("AAAA");
        SequencePersonCodeGenerator generator = new SequencePersonCodeGenerator("AAAA", "BBBB");

        assertThat(PersonCodes.next(generator, users)).isEqualTo("BBBB");
        assertThat(generator.calls()).isEqualTo(2);
    }

    @Test
    void givesUpAfterFiveCollisions() {
        personWithCode("AAAA");
        SequencePersonCodeGenerator generator = new SequencePersonCodeGenerator("AAAA");

        assertThatThrownBy(() -> PersonCodes.next(generator, users)).isInstanceOf(IllegalStateException.class);
        assertThat(generator.calls()).isEqualTo(5);
    }
}
