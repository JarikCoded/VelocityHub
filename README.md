# VelocityHub

**VelocityHub** ist ein professionelles Minecraft-Plugin für [Velocity](https://velocitypowered.com/), das als zentrale Netzwerkverwaltung für große Multi-Proxy-Minecraft-Netzwerke dient.

---

## Features

### Kern
- **Multi-Proxy-Unterstützung** via Redis Pub/Sub und Cache
- **Serververwaltung** – Alle Server mit Status, Spielerzahl, MOTD, Icon
- **Servergruppen** – Gruppenbasierte Organisation (Lobby, Survival, Skyblock, Minigames…)
- **Lobby-Items** – Automatisch bei Betreten einer Gruppe vergeben; nicht verschiebbar/droppable (konfigurierbar)
- **Server-GUI** – Serverauswahl-Inventar über Companion-Paper-Plugin-Nachrichten
- **Wartungsmodus** – Netzwerkweit, Bypass-Permission für Admins

### Synchronisierung (Redis)
| Datenpunkt | Kanal/Key |
|---|---|
| Serverstatus | `velocityhub:server:status` |
| Spieleranzahl | `velocityhub:server:players` |
| Broadcasts | `velocityhub:broadcast` |
| Maintenance | `velocityhub:maintenance` |
| MOTD | `velocityhub:motd:update` |
| Spielertransfer | `velocityhub:player:transfer` |
| Gruppen | `velocityhub:group:update` |
| Staff-/Admin-Chat | `velocityhub:msg:staff` / `:admin` |
| Private Messages | `velocityhub:msg:private` |
| Ankündigungen | `velocityhub:announcement` |

### Befehle
| Befehl | Beschreibung | Permission |
|---|---|---|
| `/server [name]` | Serverauswahl-GUI oder direkter Connect | `network.server` |
| `/send <spieler\|all> <server\|gruppe>` | Spieler senden | `network.send` |
| `/l`, `/hub`, `/lobby` | Beste Lobby | `network.lobby` |
| `/broadcast <nachricht>` | Netzwerkweiter Broadcast | `network.broadcast` |
| `/network <sub>` | Admin-Hauptbefehl | `network.network` |
| `/maintenance [on\|off]` | Wartungsmodus | `network.maintenance` |
| `/msg <spieler> <nachricht>` | Privatnachricht | `network.msg` |
| `/reply <nachricht>` | Antworten | `network.msg` |
| `/socialspy [on\|off]` | Social Spy | `network.socialspy` |
| `/staffchat [nachricht]` | Staff-Chat | `network.staffchat` |

### Erweiterte Features
- **Queue-System** mit Priority-Rängen (`network.queue.priority.<n>`)
- **Privatnachrichten** netzwerkweit (`/msg`, `/reply`)
- **Social Spy** (`/socialspy`)
- **Staff-Chat** & **Admin-Chat** netzwerkweit
- **Ankündigungen** mit Titel, ActionBar und BossBar
- **Discord-Webhook** für Broadcasts, Joins, Serverstatus
- **Spielerstatistiken** (Peak-Spieler, Uptime, Server-Joins)
- **Zuletzt besuchte Server** & **Favoriten**
- **Automatischer Reconnect** zum letzten Server (konfigurierbar)
- **MiniMessage** und **Legacy-Farbcodes** (`&`) vollständig unterstützt

---

## Installation

### Voraussetzungen
- Java 21+
- [Velocity](https://velocitypowered.com/) 3.3.0+
- Redis 6+

### Build

```bash
./gradlew shadowJar
```

Das fertige JAR befindet sich in `build/libs/VelocityHub-1.0.0.jar`.

### Setup

1. JAR in den Velocity `plugins/`-Ordner kopieren.
2. Plugin starten → Standardkonfigurationen werden erzeugt.
3. `redis.yml` anpassen (Host, Port, Passwort).
4. `servers.yml` und `groups.yml` konfigurieren.
5. Proxy-ID in `config.yml` setzen (`proxy-id: "proxy-1"` usw.).
6. `/network reload` oder Neustart.

---

## Konfigurationsdateien

| Datei | Zweck |
|---|---|
| `config.yml` | Allgemeine Einstellungen, Proxy-ID, Discord |
| `redis.yml` | Redis-Verbindung |
| `servers.yml` | Serverdefinitionen |
| `groups.yml` | Gruppenstruktur & Lobby-Items |
| `gui.yml` | GUI-Layout & -Optik |
| `messages.yml` | Alle Nachrichten (MiniMessage/Legacy) |
| `items.yml` | GUI-Item-Overrides |
| `motd.yml` | Globale MOTD |
| `permissions.yml` | Permissions-Referenz |

---

## Multi-Proxy Setup

```
Proxy-1 ──┐
Proxy-2 ──┤── Redis ──── Backend-Server
Proxy-3 ──┤
Proxy-4 ──┘
```

Alle Proxys abonnieren denselben Redis-Channel. Bei Statusänderungen (Serverstatus, Spielerzahl, Broadcast, Maintenance…) werden Nachrichten über Pub/Sub an alle anderen Proxys verteilt. Die Proxy-ID wird in `config.yml` festgelegt.

---

## Java API

```java
// Spieler verbinden
NetworkAPI.connect(player, "survival-1");
NetworkAPI.connectGroup(player, "minigames");
NetworkAPI.sendToLobby(player);

// Broadcast
NetworkAPI.broadcast("&aHello Network!");

// Daten abfragen
Optional<NetworkServer> server = NetworkAPI.getServer("lobby-1");
Collection<NetworkServer> all = NetworkAPI.getAllServers();
int online = NetworkAPI.getOnlinePlayers();
boolean maintenance = NetworkAPI.isMaintenanceEnabled();
```

### Events

| Event | Auslöser |
|---|---|
| `BroadcastEvent` | Vor einem Broadcast |
| `GroupUpdateEvent` | Gruppe geändert |
| `LobbyJoinEvent` | Spieler → Lobby |
| `PlayerNetworkSwitchEvent` | Serverwechsel |
| `ServerStatusChangeEvent` | Statusänderung eines Servers |

---

## Permissions

| Permission | Beschreibung |
|---|---|
| `network.admin` | Voller Admin-Zugriff |
| `network.server` | `/server` nutzen |
| `network.send` | `/send` nutzen |
| `network.lobby` | `/l`, `/hub`, `/lobby` |
| `network.broadcast` | `/broadcast` |
| `network.network` | `/network` Admin-Befehl |
| `network.maintenance` | Wartungsmodus umschalten |
| `network.maintenance.bypass` | Joinen während Wartung |
| `network.msg` | Privatnachrichten |
| `network.socialspy` | Social Spy |
| `network.staffchat` | Staff-Chat |
| `network.queue.priority.<n>` | Queue-Priorität (höher = früher) |
| `network.bypass.full` | Volle Server joinen |
| `network.notify.joinleave` | Join/Leave-Benachrichtigungen |

---

## Architektur

```
VelocityHubPlugin          ← Hauptplugin (@Plugin)
├── ConfigManager          ← Alle YAMLs, Hot-Reload
├── RedisManager           ← Pub/Sub + Key-Value-Cache
├── ServerManager          ← Serverzustand + Transfers
├── GroupManager           ← Gruppen + Lobby-Items
├── MaintenanceManager     ← Wartungsmodus
├── QueueManager           ← Warteschlange
├── PrivateChatManager     ← /msg, /reply, Social Spy
├── StaffChatManager       ← Staff-/Admin-Chat
├── AnnouncementManager    ← Broadcasts, Titles, BossBar
├── DiscordWebhookManager  ← Discord-Integration
├── StatisticsManager      ← Netzwerkstatistiken
├── PlayerDataManager      ← Persistente Spielerdaten (Redis)
└── GuiManager             ← Server-GUI via Plugin-Messages

API (für andere Plugins):
└── NetworkAPI             ← Statischer Zugriff auf alle Features
```

---

## GUI-Companion-Plugin

Da Velocity ein reiner Proxy ist, werden Inventar-GUIs über das **velocityhub-Plugin-Message-Protokoll** an die Backend-Server-Companion-Plugins delegiert:

- `velocityhub:gui` → Proxy sendet GUI-Daten → Backend öffnet Inventar
- `velocityhub:action` → Backend sendet Klickergebnis → Proxy verbindet Spieler
- `velocityhub:lobby-item` → Proxy sendet Item-Daten → Backend gibt Item an Spieler

---

## Technologie-Stack

- **Java 21**
- **Velocity API 3.3.0**
- **Jedis 5.1.5** (Redis)
- **Configurate 4.1.2** (YAML)
- **Adventure API** (MiniMessage, Komponenten)
- **Gson 2.11.0** (JSON)
- **OkHttp 4.12.0** (Discord Webhooks)
- **Gradle 8.10** mit Shadow-Plugin
