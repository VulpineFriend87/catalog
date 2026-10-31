package top.vulpine.catalog.network;

import top.vulpine.catalog.CatalogAction;
import top.vulpine.commons.log.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Network} whose inventories are signed with a secret every server shares.
 *
 * <p>A stored inventory starts with its signature on its own line. The signature is an HMAC-SHA256
 * of the server's name and the inventory. Inventories without a valid signature are left out. So
 * are inventories stored under another server's name.</p>
 */
public final class SignedNetwork implements Network {

    private static final String ALGORITHM = "HmacSHA256";

    private final Network network;

    private final SecretKeySpec key;

    /** The servers already reported for signing with another secret. */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /**
     * @param secret the secret every server on the network shares
     */
    public SignedNetwork(Network network, String secret) {
        this.network = network;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public CompletionStage<Boolean> claim(String server, Duration ttl) {
        return network.claim(server, ttl);
    }

    @Override
    public CompletionStage<Boolean> renew(String server, Duration ttl) {
        return network.renew(server, ttl);
    }

    @Override
    public CompletionStage<Void> release(String server) {
        return network.release(server);
    }

    @Override
    public CompletionStage<Void> publish(String server, String inventory) {
        return network.publish(server, sign(server, inventory) + "\n" + inventory);
    }

    @Override
    public CompletionStage<Map<String, String>> inventories() {

        return network.inventories().thenApply(stored -> {

            Map<String, String> out = new LinkedHashMap<>();

            for (Map.Entry<String, String> entry : stored.entrySet()) {

                String inventory = open(entry.getKey(), entry.getValue());

                if (inventory != null) {
                    out.put(entry.getKey(), inventory);
                    warned.remove(entry.getKey());
                } else if (warned.add(entry.getKey())) {
                    Logger.warn(CatalogAction.NETWORK, "Ignoring " + entry.getKey()
                            + ": it signs with a different network.secret. Copy the secret from one"
                            + " server to all the others.");
                }
            }

            return out;
        });
    }

    @Override
    public CompletionStage<Set<String>> online() {
        return network.online();
    }

    @Override
    public CompletionStage<Duration> ping() {
        return network.ping();
    }

    @Override
    public void close() {
        network.close();
    }

    /**
     * The inventory inside a stored value.
     *
     * @return the inventory, or null when the signature does not match
     */
    String open(String server, String stored) {

        int split = stored.indexOf('\n');

        if (split < 0) {
            return null;
        }

        String inventory = stored.substring(split + 1);
        byte[] expected = sign(server, inventory).getBytes(StandardCharsets.UTF_8);
        byte[] given = stored.substring(0, split).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, given) ? inventory : null;
    }

    private String sign(String server, String inventory) {

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(server.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '\n');
            byte[] signature = mac.doFinal(inventory.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            // Every JVM ships HmacSHA256.
            throw new IllegalStateException(e);
        }
    }

}
