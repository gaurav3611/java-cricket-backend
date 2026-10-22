package com.sswingg.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Builds a service account key that has the right shape but belongs to no real project.
 * It lets tests start the Firebase Admin SDK without any network access or real credentials.
 */
public final class FakeServiceAccount {

    private FakeServiceAccount() {
    }

    public static String json() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String key = Base64.getMimeEncoder(64, new byte[] {'\n'})
                    .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
            String pem = "-----BEGIN PRIVATE KEY-----\n" + key + "\n-----END PRIVATE KEY-----\n";
            return """
                    {
                      "type": "service_account",
                      "project_id": "sswingg-test",
                      "private_key_id": "test-key",
                      "private_key": "%s",
                      "client_email": "test@sswingg-test.iam.gserviceaccount.com",
                      "client_id": "1",
                      "token_uri": "https://oauth2.googleapis.com/token"
                    }
                    """.formatted(pem.replace("\n", "\\n"));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Path writeKeyFile() {
        try {
            Path file = Files.createTempFile("sswingg-fake-service-account", ".json");
            file.toFile().deleteOnExit();
            return Files.writeString(file, json());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
