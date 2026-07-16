<div align="center">
  <h1>e4all</h1>
  <p><b>A fork of e4mc that adds a toggle for Offline Mode and World Sync via GitHub.</b></p>

  <a href="https://modrinth.com/mod/e4all">
    <img src="https://img.shields.io/badge/Modrinth-Download-00AD5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth Download" />
  </a>
  <a href="https://discord.gg/mUYW9Rw2ae">
    <img src="https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord Support" />
  </a>
</div>

---

### The Core Difference: Offline Mode + World Sync
**e4all is basically e4mc, but with two major improvements: it allows you to enable or disable Offline Mode and sync your worlds via GitHub.**

While the original e4mc mod is an excellent tool for exposing LAN worlds to the internet, it strictly enforces standard Mojang authentication. **e4all** was created specifically to remove this limitation, giving you the freedom to host a world that anyone can join, regardless of their account status.

### What is e4all?
Just like the original, e4all is a reverse tunneling proxy for Minecraft. It allows you to temporarily expose your local "Open to LAN" world to the internet so friends can join from anywhere without needing to configure port forwarding or router settings.

---

### Features
* Offline Account Support: The main reason for this fork. You can now toggle **online-mode on or off** directly in the LAN menu.
* World Sync via GitHub: Automatically sync your worlds to a private GitHub repository. Upload and download worlds with a single click.
* No Configuration Required: No accounts, no external software like Hamachi, and no complex setup.
* Join with Vanilla Clients: Only the host needs to install the mod. Friends can join using a standard Minecraft client by pasting your custom domain into "Direct Connection."
* Bypass Network Restrictions: Works behind CGNAT, firewalls, and restrictive routers.

---

### How to Use

#### Basic Usage (LAN Hosting)
1. Install e4all (only required for the host).
2. Open your pause menu and click **Open to LAN**.
3. Set your desired settings and use the **Online Mode** toggle to allow or disallow offline accounts.
4. Copy the public domain generated in your chat.
5. Share the link with your friends.

#### World Sync via GitHub
1. **Create a GitHub Token:**
   - Go to https://github.com/settings/tokens
   - Click **Generate new token (classic)**
   - Set note: "e4all-world-sync"
   - Set expiration: 90 days (or longer)
   - Select scope: `repo` (full control of private repositories)
   - Generate and copy the token (starts with `ghp_`)

2. **Configure the Mod:**
   - Open Minecraft with your world loaded
   - Press the ModMenu key (default: M)
   - Click on **e4all** → **Config**
   - Click the **Lista** button to load available worlds
   - Select your world from the list
   - Click **Upload** to sync your world to GitHub

3. **Download on Another Machine:**
   - Install e4all on the target machine
   - Open ModMenu → e4all → Config
   - Click **Lista** to see available worlds
   - Select the world you want to download
   - Click **Download** to sync from GitHub

---

### Configuration File

The mod stores its configuration in `~/.minecraft/config/e4all.toml`:

```toml
# MundoSync Config
mundoSyncEnabled = true
mundoSyncUsername = "SEU_NOME_AQUI"
mundoSyncWorldPath = "/home/jack/.minecraft/saves"
mundoSyncWorldName = "NOME_DA_PASTA_DO_MUNDO"
mundoSyncWebhookUrl = "" # optional - for Discord notifications

# GitHub Config
githubToken = "ghp_SEU_TOKEN_AQUI"
githubSyncEnabled = true
```

---

### Available Commands

All commands are executed in-game with the `/e4all` prefix:

```
/e4all mundo config                  # View current configuration
/e4all mundo github init <token>     # Initialize GitHub sync with token
/e4all mundo github upload           # Upload current world to GitHub
/e4all mundo github download         # Download world from GitHub
/e4all mundo github status           # Check GitHub sync status
/e4all mundo github lista            # List available worlds on GitHub
/e4all mundo forceupload             # Force upload using rclone
/e4all mundo status                  # Check lock status
/e4all mundo lock                    # Acquire world lock
/e4all mundo unlock                  # Release world lock
/e4all mundo detectip                # Send IP to Discord webhook
/e4all mundo help                    # Show help message
```

---

### GUI Interface (ModMenu)

Access the GUI through ModMenu → e4all → Config:

- **Status Button**: Shows current status (Ready, Loading, Uploading, etc.)
- **Upload Button**: Uploads the selected world to GitHub
- **Download Button**: Downloads the selected world from GitHub
- **List Button**: Refreshes the list of available worlds from GitHub
- **World List**: Shows up to 10 available worlds (scrollable)
- **Back Button**: Returns to ModMenu

---

### Troubleshooting

#### Error: "Falha no upload/download"

**Check the following:**

1. **Is the token correct?**
   ```
   /e4all mundo github status
   ```
   - Token should appear (masked)
   - Enabled should be `true`

2. **Is the world configured correctly?**
   ```
   /e4all mundo config
   ```
   - `Mundo Path` should be the correct path
   - `Mundo Nome` should be the world folder name

3. **Does the world path exist?**
   ```bash
   ls -la /home/jack/.minecraft/saves/NOME_DO_MUNDO
   ```
   Should show the world folder

4. **Does the token have `repo` permission?**
   - Go to https://github.com/settings/tokens
   - Verify the token has the `repo` scope enabled

#### Error: "Token não configurado"

Run the initialization command again:
```
/e4all mundo github init ghp_SEU_TOKEN_AQUI
```

#### Error: "Enabled: false"

The token was not saved. Execute:
```
/e4all mundo github init ghp_SEU_TOKEN_AQUI
```

#### GUI Not Appearing

- Ensure you have **ModMenu** installed (version 18.0.0-beta.1 or later)
- Ensure you have **Fabric API** installed (version 0.153.0+ or later)
- Check the Minecraft log for errors (`latest.log`)

---

### Expected Folder Structure

```
~/.minecraft/
├── saves/
│   └── NOME_DO_MUNDO/
│       ├── level.dat
│       ├── playerdata/
│       └── ...
├── config/
│   └── e4all.toml  (configuration file)
└── mods/
    ├── e4all-fabric-1.6.2.jar
    ├── fabric-api-0.153.0+26.1.2.jar
    └── modmenu-18.0.0-beta.1.jar
```

---

### Dependencies

- **Minecraft**: 26.1.2 or later
- **Fabric Loader**: 0.19.3 or later
- **Fabric API**: 0.153.0+ or later
- **ModMenu**: 18.0.0-beta.1 or later (for GUI configuration)

---

### How It Works

#### World Sync
- Worlds are stored in private GitHub repositories named `e4all-world-NOME_DO_MUNDO`
- The mod uses the GitHub API to list, upload, and download worlds
- Uploads are performed asynchronously to avoid blocking the game
- Downloads replace the local world folder with the GitHub version

#### Lock System
- Prevents conflicts when multiple players sync the same world
- Locks are stored in the GitHub repository
- Use `/e4all mundo lock` before playing and `/e4all mundo unlock` when done

#### Reverse Tunneling (e4mc)
- Uses reverse tunneling to expose your LAN world to the internet
- No port forwarding required
- Works behind CGNAT and firewalls
- Generates a custom domain for friends to connect

---

### Known Issues

1. **GUI rendering with Iris shaders**: Text and shapes may not render correctly when using certain shader packs. The interface uses buttons only to ensure compatibility.
2. **Large worlds**: Uploading worlds larger than 1GB may take several minutes depending on your connection.
3. **GitHub rate limits**: The GitHub API has rate limits. If you exceed them, wait a few minutes before trying again.

---

### Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

---

### Credits and Support

This project is a fork of the original [e4mc](https://modrinth.com/mod/e4mc). I appreciate the work done by the original developers and created this version specifically to provide the community with the Offline Mode and World Sync features.

- **Original e4mc**: https://modrinth.com/mod/e4mc
- **Support & Suggestions**: If you have problems or want to suggest new features, join the Discord: https://discord.gg/mUYW9Rw2ae or write an issue on GitHub

---

### License

MIT License - feel free to use and modify as needed.