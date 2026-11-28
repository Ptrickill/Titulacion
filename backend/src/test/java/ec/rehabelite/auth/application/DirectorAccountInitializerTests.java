package ec.rehabelite.auth.application;

import ec.rehabelite.TestClinicalText;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * MEJ-05: without an active operations director, the initializer creates one only from complete variables and an
 * unused email; otherwise it creates nothing (and only logs an error).
 */
class DirectorAccountInitializerTests {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @Test
    void createsNothingWhileAnActiveDirectorExists() {
        when(users.existsActiveWithRole(Role.OPERATIONS_DIRECTOR)).thenReturn(true);

        initializer("director@rehabelite.test", "Clave.Segura.2026").run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
    }

    @Test
    void createsNothingWithoutTheVariables() {
        initializer("", "").run(new DefaultApplicationArguments());
        initializer("director@rehabelite.test", null).run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void createsNothingWhenTheEmailBelongsToAnotherAccount() {
        when(users.findByEmailOrPendingEmail("recepcion@rehabelite.test", "recepcion@rehabelite.test"))
                .thenReturn(List.of(mock(User.class)));

        initializer("Recepcion@rehabelite.test", "Clave.Segura.2026").run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
    }

    @Test
    void createsTheDirectorWhenNoneIsActive() {
        when(encoder.encode("Clave.Segura.2026")).thenReturn("$2a$10$hash");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        initializer("director@rehabelite.test", "Clave.Segura.2026").run(new DefaultApplicationArguments());

        verify(users).save(any(User.class));
        verify(events).publishEvent(any(Object.class));
    }

    private DirectorAccountInitializer initializer(String email, String password) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("test-only-secret-for-director-initializer-0123456789", 30),
                new AppProperties.InitialDirector(email, password),
                new AppProperties.Cors(List.of("http://localhost:4200")), new AppProperties.Ai(null), null,
                "http://localhost:4200", new AppProperties.Mail("no-reply@rehabelite.test",
                        AppProperties.MailTransport.SMTP, new AppProperties.Brevo(null, "https://api.brevo.com",
                                Duration.ofSeconds(5), Duration.ofSeconds(10))),
                new AppProperties.Tokens(Duration.ofHours(48), Duration.ofHours(1)),
                new AppProperties.Clinic(LocalTime.of(7, 0), LocalTime.of(20, 0)),
                new AppProperties.ClinicalData(TestClinicalText.KEY, ""),
                new AppProperties.Session(Duration.ofMinutes(15), Duration.ofMinutes(2)),
                new AppProperties.Compliance(15));
        return new DirectorAccountInitializer(users, encoder, properties, events,
                Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC));
    }

}
