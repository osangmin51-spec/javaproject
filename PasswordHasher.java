import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

class PasswordHasher {
    private static final String PREFIX = "sha256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static String hash(String rawPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] digest = digest(salt, rawPassword);
        return PREFIX + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(digest);
    }

    static boolean matches(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        if (!storedPassword.startsWith(PREFIX + "$")) {
            return storedPassword.equals(rawPassword);
        }
        String[] parts = storedPassword.split("\\$");
        if (parts.length != 3) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = digest(salt, rawPassword);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static boolean needsUpgrade(String storedPassword) {
        return storedPassword == null || !storedPassword.startsWith(PREFIX + "$");
    }

    private static byte[] digest(byte[] salt, String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing failed.", ex);
        }
    }
}
