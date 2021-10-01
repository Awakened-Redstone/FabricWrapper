# FabricWrapper [![CodeQL](https://github.com/Awakened-Redstone/FabricWrapper/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/Awakened-Redstone/FabricWrapper/actions/workflows/codeql-analysis.yml)
Installs a Minecraft server on specified game version with Fabric loader installed

### The wrapper NOT a mod, it is a pre loader, you need to run it instead of `server.jar` or `fabric-server-launch.jar`

**IT WORKS WITH DEDICATED SERVER ONLY, IT DOES NOT INSTALL CLIENT**

Only works with Minecraft versions supported by FabricMC, does not work with FabricMC forks

Allows installing the server on a specific version, on the latest game release or on the latest snapshot 
(on snapshot option it will install the 1st game version on fabric meta site)

Accepted inputs on `gameVersion` in `fabric-wrapper.properties`:
- `latest/release` or `latest/stable` for the latest release 
- `latest/snapshot` or `latest/unstable` for the latest snapshot
- any game version you can install FabricMC

I do not guarantee it to be stable and work on any version.  
Crashes caused by mods due to version support may occur when using `latest/release` or `latest/snapshot`  
Some mods may not work with the wrapper.  
It is not guaranteed to work.
