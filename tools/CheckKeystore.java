import java.io.File;
import java.security.Key;
import java.security.KeyStore;

/**
 * Checks that a keystore and the passwords for it actually work together, the
 * way the Android Gradle plugin will use them.
 *
 * CI runs this before building, because a wrong alias or key password otherwise
 * surfaces minutes later, from inside packageRelease, as "Given final block not
 * properly padded". keytool is no help here: for a PKCS12 keystore it does not
 * check the key password at all, while the plugin does.
 *
 *   KEYSTORE_FILE=... RELEASE_KEYSTORE_PASSWORD=... RELEASE_KEY_ALIAS=... \
 *     RELEASE_KEY_PASSWORD=... java tools/CheckKeystore.java
 *
 * Everything comes from the environment: passwords on a command line would show
 * up in the process list.
 */
public class CheckKeystore {
    public static void main(String[] arguments) {
        String alias = System.getenv("RELEASE_KEY_ALIAS");
        try {
            KeyStore keystore = KeyStore.getInstance(
                new File(System.getenv("KEYSTORE_FILE")),
                System.getenv("RELEASE_KEYSTORE_PASSWORD").toCharArray());
            Key key = keystore.getKey(alias, System.getenv("RELEASE_KEY_PASSWORD").toCharArray());
            if (key == null) {
                System.err.println("The keystore holds no key under the alias " + alias + ".");
                System.err.println("It holds: " + String.join(", ", java.util.Collections.list(keystore.aliases())));
                System.exit(1);
            }
        } catch (Exception failure) {
            System.err.println(failure);
            System.exit(1);
        }
    }
}
