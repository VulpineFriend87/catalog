package top.vulpine.catalog.modrinth.model;

import com.google.gson.annotations.SerializedName;

/**
 * Release channel of a Modrinth version.
 *
 * <p>Declared from most to least stable. {@link #accepts(ReleaseChannel)} relies on the order.</p>
 */
public enum ReleaseChannel {

    @SerializedName("release")
    RELEASE,

    @SerializedName("beta")
    BETA,

    @SerializedName("alpha")
    ALPHA;

    /**
     * Whether a server subscribed to this channel should be offered the given version.
     *
     * <p>Channels are cumulative: a server on {@code BETA} is also offered releases.</p>
     *
     * @param other the channel of the candidate version
     * @return true if the candidate is at least as stable as this channel
     */
    public boolean accepts(ReleaseChannel other) {
        return other.ordinal() <= this.ordinal();
    }

    /**
     * The channels a server subscribed to this one should ask the API for.
     *
     * @return every channel this one accepts
     */
    public ReleaseChannel[] included() {
        ReleaseChannel[] values = values();
        ReleaseChannel[] included = new ReleaseChannel[ordinal() + 1];
        System.arraycopy(values, 0, included, 0, included.length);
        return included;
    }

    /**
     * The lowercase name Modrinth uses for this channel in request bodies and query strings.
     *
     * @return the API representation
     */
    public String apiName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

}
