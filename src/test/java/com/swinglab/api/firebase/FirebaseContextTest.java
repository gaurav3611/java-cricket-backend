package com.swinglab.api.firebase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swinglab.api.FakeServiceAccount;
import org.junit.jupiter.api.Test;

class FirebaseContextTest {

    private static final String NO_FILE = "target/no-such-service-account.json";

    @Test
    void noCredentialsMeansDemoMode() {
        FirebaseContext context = new FirebaseContext("", NO_FILE);
        assertTrue(context.isDemoMode());
        assertThrows(IllegalStateException.class, context::auth);
        assertThrows(IllegalStateException.class, context::firestore);

        assertTrue(new FirebaseContext("   ", "").isDemoMode());
    }

    @Test
    void keyGivenAsTextTurnsDemoModeOff() {
        FirebaseContext context = new FirebaseContext(FakeServiceAccount.json(), NO_FILE);
        try {
            assertFalse(context.isDemoMode());
            assertNotNull(context.auth());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void keyGivenAsFileTurnsDemoModeOff() {
        FirebaseContext context = new FirebaseContext("", FakeServiceAccount.writeKeyFile().toString());
        try {
            assertFalse(context.isDemoMode());
            assertNotNull(context.auth());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void brokenCredentialsStopTheApplicationInsteadOfDisablingAuth() {
        assertThrows(IllegalStateException.class, () -> new FirebaseContext("{ this is not a key", NO_FILE));
        assertThrows(IllegalStateException.class, () -> new FirebaseContext("{\"type\": \"service_account\"}", NO_FILE));
    }
}
