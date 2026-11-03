package dev.pvpplus.client;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Encodes a reachable host address and TCP port into a shareable room code.
 * The code is an encoding, not encryption: it does not hide the endpoint.
 */
public final class RoomCode {
    private static final String PREFIX = "PV4-";
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    private RoomCode() {}

    public record Endpoint(String host, int port) {}

    public static String encode(String host, int port) {
        String normalizedHost = normalizeHost(host);
        validatePort(port);
        String payload = normalizedHost + "|" + port;
        String signed = payload + "|" + checksum(payload);
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signed.getBytes(StandardCharsets.UTF_8));
    }

    public static Endpoint decode(String code) {
        if (code == null) throw new IllegalArgumentException("Введите код комнаты");
        String trimmed = code.trim();
        if (trimmed.length() < 10 || trimmed.length() > 512 || !trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw new IllegalArgumentException("Код комнаты имеет неверный формат");
        }
        final String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Код комнаты повреждён");
        }
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Код комнаты повреждён");
        String host = normalizeHost(parts[0]);
        final int port;
        try { port = Integer.parseInt(parts[1]); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("В коде указан неверный порт"); }
        validatePort(port);
        String payload = host + "|" + port;
        if (!checksum(payload).equalsIgnoreCase(parts[2])) throw new IllegalArgumentException("Код комнаты повреждён или введён с ошибкой");
        return new Endpoint(host, port);
    }

    private static String normalizeHost(String value) {
        if (value == null) throw new IllegalArgumentException("Укажите IP-адрес или домен для приглашения");
        String host = value.trim();
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) host = host.substring(1, host.length() - 1);
        if (host.isEmpty() || host.length() > 253 || !host.matches("[A-Za-z0-9._:%-]+")) {
            throw new IllegalArgumentException("Укажите корректный IP-адрес или домен без порта");
        }
        if (host.startsWith(".") || host.endsWith(".") || host.contains("..") || host.contains("|")) {
            throw new IllegalArgumentException("Укажите корректный IP-адрес или домен");
        }
        return host;
    }

    private static void validatePort(int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("Порт должен быть от 1024 до 65535");
        }
    }

    private static String checksum(String payload) {
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08x", crc.getValue());
    }

    /** Picks a likely invite address. The owner can still edit it for LAN/VPN/public routing. */
    public static String detectInviteAddress() {
        List<AddressCandidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                try { if (!nic.isUp() || nic.isLoopback()) continue; }
                catch (SocketException ignored) { continue; }
                String nicName = (nic.getName() + " " + nic.getDisplayName()).toLowerCase(Locale.ROOT);
                boolean vpnInterface = nicName.matches(".*(radmin|hamachi|zerotier|tailscale|wireguard|vpn).*" );
                if (nic.isVirtual() && !vpnInterface) continue;
                int interfacePriority = vpnInterface ? 0 : 1;
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                    int addressPriority = address.isSiteLocalAddress() ? 0 : 1;
                    candidates.add(new AddressCandidate(address.getHostAddress(), interfacePriority, addressPriority));
                }
            }
        } catch (SocketException ignored) { }
        return candidates.stream()
                .sorted(Comparator.comparingInt(AddressCandidate::interfacePriority).thenComparingInt(AddressCandidate::addressPriority))
                .map(AddressCandidate::address).findFirst().orElse("");
    }

    private record AddressCandidate(String address, int interfacePriority, int addressPriority) {}
}
