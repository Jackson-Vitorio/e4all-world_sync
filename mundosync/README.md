# MundoSync - Minecraft World Sync via GitHub

MundoSync is a Minecraft mod that allows you to sync your worlds via GitHub. Upload and download worlds with a single click directly from the ModMenu interface.

## Features

- Upload worlds to private GitHub repositories
- Download worlds from GitHub
- List all available worlds
- Simple GUI interface via ModMenu
- Async operations (no game freezing)
- Discord webhook support (optional)
- Compatible with e4all mod (uses its configuration if available)

## Installation

### Requirements

- Minecraft 26.1.2 or later
- Fabric Loader 0.19.3 or later
- Fabric API 0.153.0+ or later
- ModMenu 18.0.0-beta.1 or later (for GUI)

### Setup

1. Install Fabric Loader
2. Download and install Fabric API
3. Download and install ModMenu
4. Download MundoSync mod
5. Place all mods in `.minecraft/mods/` folder
6. Launch Minecraft

## Configuration

### GitHub Token Setup

1. Go to https://github.com/settings/tokens
2. Click **Generate new token (classic)**
3. Set note: "mundosync"
4. Set expiration: 90 days (or longer)
5. Select scope: `repo` (full control of private repositories)
6. Generate and copy the token (starts with `ghp_`)

### Mod Configuration

#### Via GUI (ModMenu)

1. Press ModMenu key (default: M)
2. Click on **MundoSync** → **Config**
3. Click **List** button to load available worlds
4. Select your world from the list
5. Click **Upload** to sync your world to GitHub

#### Via Configuration File

The mod stores its configuration in `~/.minecraft/config/mundosync.properties`:

```properties
# MundoSync Config
username=SEU_NOME_AQUI
worldPath=/home/jack/.minecraft/saves
worldName=NOME_DA_PASTA_DO_MUNDO
webhookUrl= # optional - for Discord notifications

# GitHub Config
githubToken=ghp_SEU_TOKEN_AQUI
githubSyncEnabled=true
```

## Usage

### GUI Interface

Access the GUI through ModMenu → MundoSync → Config:

- **Status Button**: Shows current status (Ready, Loading, Uploading, etc.)
- **Upload Button**: Uploads the selected world to GitHub
- **Download Button**: Downloads the selected world from GitHub
- **List Button**: Refreshes the list of available worlds from GitHub
- **World List**: Shows up to 10 available worlds (scrollable)
- **Back Button**: Returns to ModMenu

### Commands

All commands are executed in-game with the `/mundosync` prefix:

```
/mundosync config                  # View current configuration
/mundosync github init <token>     # Initialize GitHub sync with token
/mundosync github upload           # Upload current world to GitHub
/mundosync github download         # Download world from GitHub
/mundosync github status           # Check GitHub sync status
/mundosync github list             # List available worlds on GitHub
/mundosync help                    # Show help message
```

## How It Works

### World Sync

- Worlds are stored in private GitHub repositories named `e4all-world-NOME_DO_MUNDO`
- The mod uses the GitHub API to list, upload, and download worlds
- Uploads are performed asynchronously to avoid blocking the game
- Downloads replace the local world folder with the GitHub version

### Compatibility with e4all

If e4all is installed alongside MundoSync:
- MundoSync will use e4all's configuration (username, world path)
- No conflicts between mods
- Both mods can coexist peacefully

## Troubleshooting

### Error: "Upload/Download failed"

**Check the following:**

1. **Is the token correct?**
   ```
   /mundosync github status
   ```
   - Token should appear (masked)
   - Enabled should be `true`

2. **Is the world configured correctly?**
   ```
   /mundosync config
   ```
   - `World Path` should be the correct path
   - `World Name` should be the world folder name

3. **Does the world path exist?**
   ```bash
   ls -la /home/jack/.minecraft/saves/NOME_DO_MUNDO
   ```
   Should show the world folder

4. **Does the token have `repo` permission?**
   - Go to https://github.com/settings/tokens
   - Verify the token has the `repo` scope enabled

### Error: "Token not configured"

Run the initialization command again:
```
/mundosync github init ghp_SEU_TOKEN_AQUI
```

### Error: "Enabled: false"

The token was not saved. Execute:
```
/mundosync github init ghp_SEU_TOKEN_AQUI
```

### GUI Not Appearing

- Ensure you have **ModMenu** installed (version 18.0.0-beta.1 or later)
- Ensure you have **Fabric API** installed (version 0.153.0+ or later)
- Check the Minecraft log for errors (`latest.log`)

## Expected Folder Structure

```
~/.minecraft/
├── saves/
│   └── NOME_DO_MUNDO/
│       ├── level.dat
│       ├── playerdata/
│       └── ...
├── config/
│   └── mundosync.properties  (configuration file)
└── mods/
    ├── mundosync-fabric-1.0.0.jar
    ├── fabric-api-0.153.0+26.1.2.jar
    └── modmenu-18.0.0-beta.1.jar
```

## Dependencies

- **Minecraft**: 26.1.2 or later
- **Fabric Loader**: 0.19.3 or later
- **Fabric API**: 0.153.0+ or later
- **ModMenu**: 18.0.0-beta.1 or later (optional, for GUI)

## Known Issues

1. **GUI rendering with Iris shaders**: Text and shapes may not render correctly when using certain shader packs. The interface uses buttons only to ensure compatibility.
2. **Large worlds**: Uploading worlds larger than 1GB may take several minutes depending on your connection.
3. **GitHub rate limits**: The GitHub API has rate limits. If you exceed them, wait a few minutes before trying again.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Credits

This mod was originally developed as part of the e4all project and later extracted into a standalone mod.

## License

MIT License - feel free to use and modify as needed.