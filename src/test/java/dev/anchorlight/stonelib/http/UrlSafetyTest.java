package dev.anchorlight.stonelib.http;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSafetyTest {

    @Test
    void allowsHttpsScheme() {
        assertTrue(UrlSafety.isSchemeAllowed(URI.create("https://cdn.instagram.com/img.jpg")));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertFalse(UrlSafety.isSchemeAllowed(URI.create("file:///etc/passwd")));
        assertFalse(UrlSafety.isSchemeAllowed(URI.create("ftp://example.com/x")));
        assertFalse(UrlSafety.isSchemeAllowed(URI.create("gopher://example.com/x")));
        assertFalse(UrlSafety.isSchemeAllowed(URI.create("jar:file:///etc/passwd!/x")));
    }

    @Test
    void rejectsHttpSchemeByDefault() {
        assertFalse(UrlSafety.isSchemeAllowed(URI.create("http://cdn.instagram.com/img.jpg")));
    }

    @Test
    void rejectsHttpSchemeWhenHttpsOnlyExplicit() {
        assertFalse(UrlSafety.isSchemeAllowed(URI.create("http://cdn.instagram.com/img.jpg"), false));
    }

    @Test
    void allowsHttpSchemeOnlyWhenExplicitlyAllowed() {
        assertTrue(UrlSafety.isSchemeAllowed(URI.create("http://cdn.instagram.com/img.jpg"), true));
        assertTrue(UrlSafety.isSchemeAllowed(URI.create("https://cdn.instagram.com/img.jpg"), true));
    }

    @Test
    void blocksLoopbackHostnames() {
        assertTrue(UrlSafety.isHostBlocked("localhost"));
        assertTrue(UrlSafety.isHostBlocked("LOCALHOST"));
    }

    @Test
    void blocksLoopbackAddresses() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("127.0.0.1")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("::1")));
    }

    @Test
    void blocksPrivateRfc1918Addresses() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("10.0.0.5")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("172.16.0.5")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("192.168.1.5")));
    }

    @Test
    void allowsAddressesJustOutsidePrivateRfc1918Boundaries() throws UnknownHostException {
        // 172.15.x.x and 172.32.x.x are public — verify the 16-31 boundary is exact, not off-by-one.
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("172.15.255.255")));
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("172.32.0.0")));
    }

    @Test
    void blocksUniqueLocalIpv6Addresses() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("fd00::1")));
    }

    @Test
    void blocksLinkLocalAndMulticastAddresses() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("169.254.1.1")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("224.0.0.1")));
    }

    @Test
    void blocksLinkLocalIpv6Addresses() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("fe80::1")));
    }

    @Test
    void allowsPublicAddresses() throws UnknownHostException {
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("93.184.216.34")));
    }

    @Test
    void blocksIpv4MappedIpv6LoopbackAddress() throws UnknownHostException {
        // ::ffff:127.0.0.1 is the classic IPv4-mapped-IPv6 SSRF bypass: a naive check that only
        // inspects Inet6Address.isLoopbackAddress() catches this already, but a check that only
        // special-cased Inet4Address would miss it entirely, so this pins the unwrap-then-check path.
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("::ffff:127.0.0.1")));
    }

    @Test
    void blocksIpv4MappedIpv6PrivateAddress() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("::ffff:10.0.0.5")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("::ffff:192.168.1.1")));
    }

    @Test
    void allowsIpv4MappedIpv6PublicAddress() throws UnknownHostException {
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("::ffff:93.184.216.34")));
    }

    @Test
    void rejectsMaliciousRedirectTarget() throws UnknownHostException {
        // Simulates the per-redirect-hop re-validation: an initial public URL's response
        // redirects to a Location that resolves to an internal address. The caller is
        // expected to re-resolve and re-check every hop with isAddressBlocked, not just
        // the initial URL, and this test pins that the check catches an internal target.
        URI initialRequest = URI.create("https://cdn.instagram.com/img.jpg");
        assertTrue(UrlSafety.isSchemeAllowed(initialRequest));
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("93.184.216.34")));

        URI redirectTarget = URI.create("https://internal.attacker-controlled.example/x");
        assertTrue(UrlSafety.isSchemeAllowed(redirectTarget));
        InetAddress redirectResolvedAddress = InetAddress.getByName("169.254.169.254");
        assertTrue(UrlSafety.isAddressBlocked(redirectResolvedAddress));
    }

    @Test
    void rejectsNullInputs() {
        assertTrue(UrlSafety.isHostBlocked(null));
        assertTrue(UrlSafety.isAddressBlocked(null));
        assertFalse(UrlSafety.isSchemeAllowed(null));
    }

    @Test
    void blocksThisNetworkAndCarrierGradeNatRanges() throws UnknownHostException {
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("0.1.2.3")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("100.64.0.1")));
        assertTrue(UrlSafety.isAddressBlocked(InetAddress.getByName("100.127.255.254")));
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("100.63.255.255")));
        assertFalse(UrlSafety.isAddressBlocked(InetAddress.getByName("100.128.0.0")));
    }
}
