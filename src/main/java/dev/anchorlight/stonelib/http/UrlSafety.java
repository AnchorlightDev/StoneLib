package dev.anchorlight.stonelib.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF defence for outbound HTTP calls made against URLs a third party can influence (API
 * responses, operator-supplied links). Every URL - the initial request and every redirect hop's
 * destination - should pass {@link #isSchemeAllowed(URI, boolean)}, {@link #isHostBlocked(String)}
 * and {@link #isAddressBlocked(InetAddress)} before a connection is opened, since redirect-based
 * SSRF (DNS rebinding, an open redirect to an internal address) is a realistic attack against a
 * plugin that fetches attacker-influenced URLs. {@link SafeDownloader} does all of this per hop.
 *
 * <p>DNS resolution is deliberately not performed by this class: the caller resolves
 * each hostname once (so it can re-validate the resolved address at every redirect
 * hop rather than trusting a hostname that could resolve differently between checks).
 */
public final class UrlSafety {

    private static final Set<String> BLOCKED_HOSTNAMES =
        Set.of("localhost", "localhost.localdomain", "ip6-localhost");

    private UrlSafety() {
    }

    /**
     * Scheme check using the default (https-only) policy. Equivalent to
     * {@code isSchemeAllowed(uri, false)}.
     */
    public static boolean isSchemeAllowed(URI uri) {
        return isSchemeAllowed(uri, false);
    }

    /**
     * Returns true only for {@code https}, and for {@code http} only when
     * {@code allowHttp} is explicitly true (per config). Any other scheme
     * (file, ftp, gopher, jar, etc.) is always rejected.
     */
    public static boolean isSchemeAllowed(URI uri, boolean allowHttp) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        if (scheme.equalsIgnoreCase("https")) {
            return true;
        }
        return allowHttp && scheme.equalsIgnoreCase("http");
    }

    /**
     * Returns true if the given host string is a known loopback hostname alias.
     * This is a cheap pre-DNS check; the authoritative check is
     * {@link #isAddressBlocked(InetAddress)} on the resolved address.
     */
    public static boolean isHostBlocked(String host) {
        if (host == null) {
            return true;
        }
        return BLOCKED_HOSTNAMES.contains(host.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns true if the given resolved address is loopback, unspecified,
     * link-local, multicast, or within a non-public range (RFC1918 IPv4, the
     * "this network" block 0.0.0.0/8, carrier-grade NAT 100.64.0.0/10, IPv6
     * unique-local fc00::/7). IPv4-mapped IPv6 addresses (e.g. {@code ::ffff:127.0.0.1})
     * are unwrapped to their IPv4 form before evaluation so they cannot bypass the
     * IPv4 range checks.
     */
    public static boolean isAddressBlocked(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address instanceof Inet6Address v6) {
            address = unwrapIpv4MappedAddress(v6, address);
        }

        if (address.isLoopbackAddress()
            || address.isAnyLocalAddress()
            || address.isLinkLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address v4) {
            return isBlockedIpv4(v4);
        }
        if (address instanceof Inet6Address v6) {
            return isBlockedIpv6(v6);
        }
        return false;
    }

    private static InetAddress unwrapIpv4MappedAddress(Inet6Address v6, InetAddress fallback) {
        byte[] bytes = v6.getAddress();
        // ::ffff:a.b.c.d mapped form: bytes[10]=0xFF, bytes[11]=0xFF, last 4 bytes are the IPv4 address.
        boolean mapped = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                mapped = false;
                break;
            }
        }
        if (mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
            try {
                byte[] v4Bytes = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                return InetAddress.getByAddress(v4Bytes);
            } catch (java.net.UnknownHostException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean isBlockedIpv4(Inet4Address v4) {
        byte[] bytes = v4.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        if (first == 0 || first == 10) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        return false;
    }

    private static boolean isBlockedIpv6(Inet6Address v6) {
        byte[] bytes = v6.getAddress();
        int first = bytes[0] & 0xFF;
        // Unique local addresses fc00::/7 (top 7 bits = 1111 110).
        return (first & 0xFE) == 0xFC;
    }
}
