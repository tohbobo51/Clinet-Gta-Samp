SA-MP Mobile Client (GTA: SA 2.10)
Refactored & Maintained by NoobCoder

I've refactored this client to fix a lot of the persistent crashes, memory leaks, and multiplayer bugs present in older mobile sources, turning it into a much more stable base for further development.

📌 What's Fixed & Improved
Enhanced Stability: Patched core crash vectors and fixed network/gameplay bugs from previous builds.

Multi-Arch Support: Full compatibility with both ARMv7 (32-bit) and ARMv8 (64-bit) devices.

Refactored Base: Optimized for smoother performance and easier ongoing maintenance.

⚠️ Note: Single-player mode is intentionally disabled. This build is strictly configured for SA-MP multiplayer connections.

🛠️ Environment & Prerequisites
Android NDK: 28

JDK: 21

Game Cache: Requires clean GTA: SA v2.10 data files.

📂 Key Source Files
If you're looking to modify configurations or core hooks, start here:

main.cpp — Server IP, port configuration, and connection logic.

Streaming.cpp — File streaming and archive hooks.

game.cpp — Core game engine logic and internal hooks.

🚀 Setup & Build Instructions
Open main.cpp and set your server's IP and port.

Build the APK using NDK 28 and Java 21.

Make sure the target device has the GTA: SA 2.10 game cache installed, then run the client.