# RCON Fix

A Forge 1.7.10 server mod that makes RCON work, and makes it safer to leave on. Built for GT New Horizons dev servers and test copies of real servers, so scripts can send console commands to a running server and read the replies.

## What's wrong with vanilla 1.7.10 RCON

`RConThreadClient.run()` wraps its read loop body in `try … finally { closeSocket(); }`. A `continue` still runs the `finally`, so the server closes the connection right after it answers the login packet. The next loop pass calls `getInputStream()` on the now-null socket and logs `Exception whilst parsing RCON input` with a `NullPointerException`. No command ever runs, whatever client you use.

Vanilla also:

- runs commands on the RCON connection's thread instead of the server thread (Hodgepodge's `fixRconThreading` covers this in the full pack, but not in dev runs);
- listens on `server-ip`, or on every interface when that's empty, which is the usual setting on a hosted server;
- allows unlimited password guesses, and compares the password with `String.equals`;
- reads each packet with a single `read()` of up to 1460 bytes, so a packet split across TCP segments fails.

## What this mod changes

| | Vanilla | With RCON Fix |
|---|---|---|
| Connection | Closed after login | Stays open until the client leaves or goes idle |
| Where commands run | RCON thread | Server thread, at the start of the next tick |
| Bind address | `server-ip` or `0.0.0.0` | `127.0.0.1` unless `bindAddress` says otherwise |
| Short password on a reachable address | Allowed | RCON refuses to start (under 16 characters by default) |
| Wrong passwords | Unlimited | 5 per address, then a 5-minute lockout that doubles each time, up to a day |
| Idle connections | Kept forever | Closed after 10 s without a login, or 10 minutes without a packet once logged in |
| Allow list | None | Optional list of client IPs |

The wire protocol is unchanged, so any RCON client works. The mod does nothing on clients.

## Install

1. Download `rconfix-<version>.jar` from this repo's Releases and put it in the server's `mods` folder. It needs UniMixins, which GTNH already has.
2. In `server.properties`, set `enable-rcon=true`, a `rcon.port`, and a long random `rcon.password`.
3. Start the server. The mod writes `config/rconfix.cfg`; the defaults keep RCON on 127.0.0.1.

For a dev server in another Gradle project, add the `-dev` jar to `runServer` and name the core mod, since FML only loads core mods from the classpath when they're listed. JourneySync's `dependencies.gradle` and `addon.gradle` do this:

```groovy
runtimeOnlyNonPublishable(files("../gtnh-rcon-fix/build/libs/rconfix-0.1.0-snapshot-dev.jar"))
// and in addon.gradle, plus forceEnableMixins = true in gradle.properties:
tasks.named("runServer", JavaExec).configure { systemProperty("fml.coreMods.load", "com.rconfix.core.RconFixCore") }
```

## Sending commands

`scripts/rcon.py` reads the address, port and password from a `server.properties`, so the password never appears on a command line:

```bash
python scripts/rcon.py --properties ../JourneySync/run/server/server.properties "list" "tp Alex 2000 100 2000"
```

It prints each command and its reply, and exits with code 1 if the login fails. For a remote server, `--host <address>` overrides the address in the file, which on a hosted server is usually `0.0.0.0`.

## Hosted servers (Akliz)

On a host, connections reach the server through a port forward, so RCON has to listen on every interface. Lock it down with the allow list and a long password:

1. In Akliz's Command Center, open the server's Port Forwarding page and request an "Other" TCP port with "One to One" ticked, so the public and private port numbers match.
2. In `server.properties`, set `enable-rcon=true`, `rcon.port` to that number, and `rcon.password` to a long random string of letters and digits (Akliz's panel rejects other characters).
3. In `config/rconfix.cfg`, set `bindAddress=0.0.0.0` and list your own public IP under `allowedAddresses`. If your IP changes, RCON refuses you until you update the list.
4. Start the server. The log says `RCON running on 0.0.0.0:<port>`, and each connection logs the client's address.

```bash
python scripts/rcon.py --properties server.properties --host <server IP> "list"
```

The password and commands still cross the internet in plain text until the encrypted listener below exists.

## Config

`config/rconfix.cfg`:

| Section | Key | Default | What it does |
|---|---|---|---|
| network | `bindAddress` | `127.0.0.1` | Address RCON listens on. Empty means vanilla behaviour |
| network | `allowedAddresses` | empty | Client IPs allowed to connect. Empty allows any client that can reach the listener |
| security | `minRemotePasswordLength` | `16` | RCON won't start on a non-loopback address with a shorter password |
| security | `maxFailedLogins` | `5` | Wrong passwords from one address before it's locked out |
| security | `lockoutSeconds` | `300` | First lockout; each further lockout doubles, up to a day |
| security | `loginTimeoutSeconds` | `10` | Close connections that haven't logged in by then |
| security | `idleTimeoutSeconds` | `600` | Close logged-in connections that send nothing for this long |
| commands | `commandTimeoutSeconds` | `30` | Reply with an error if the server thread hasn't run the command by then |

Every login, failed login and command is logged with the client's address.

## Planned: encrypted remote access

RCON sends the password and every command in plain text, so it should only cross a network you trust. For a hosted server such as Akliz, the plan is a second, optional listener:

- TLS on its own port, carrying the same RCON packets, so plain RCON can stay on 127.0.0.1.
- On first start the mod creates a self-signed certificate with the JRE's `keytool` and logs its SHA-256 fingerprint. `rcon.py --tls --fingerprint <sha256>` refuses any other certificate, which stops a man in the middle without a certificate authority.
- The RCON password, lockout and allow list still apply.
- Possible later: client certificates instead of, or as well as, the password.

## Developing

```powershell
.\dev.ps1 build            # compile, unit tests, jar in build\libs
.\dev.ps1 spotlessApply    # GTNH formatting (the build fails without it)
```

Unit tests cover packet framing, the lockout rules, and real loopback sessions: several commands on one connection, wrong passwords, lockout, the allow list and the login timeout. The Mixins were checked on JourneySync's dev server (Forge 1.7.10, UniMixins): RCON bound to 127.0.0.1, three commands ran on one connection, and the log shows them executing on `Server thread`.

On the full GTNH 2.9.0 pack, where Hodgepodge 2.7.221's RCON fix also applies, it drove a local copy of a real server through JourneySync's multi-player tests: game modes, teleports, whitelist changes, mod commands and `stop`, many commands per connection. On a live Akliz server it listens on 0.0.0.0 behind a one-to-one port forward with an allow list, and runs commands such as `list` and `dynmap fullrender` from a home PC.

## License

MIT, see [LICENSE](LICENSE). The build setup comes from GTNH's [ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10), also MIT ([LICENSE-ExampleMod1.7.10](LICENSE-ExampleMod1.7.10)).
