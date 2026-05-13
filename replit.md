# Botcraft

## Overview

Botcraft is a C++ library for creating Minecraft bots. It supports all official Minecraft releases from 1.12.2 to the latest version. The library provides:

- Connection to Minecraft servers (offline/cracked mode and online Microsoft account mode)
- Full physics and collision simulation
- World data, entity support
- Behaviour tree system for bot programming
- Optional OpenGL rendering

## Project Structure

- `botcraft/` - Main bot library source code
- `protocolCraft/` - Minecraft protocol implementation (sublibrary)
- `Examples/` - Example bots demonstrating various features
- `3rdparty/` - Third-party dependencies (asio, zlib, openssl, etc.)
- `cmake/` - CMake build scripts
- `Assets/` - Minecraft game assets
- `bin/` - Build output (compiled libraries and examples)
- `build/` - CMake build directory

## Build System

This project uses CMake with C++17. Key CMake options:
- `BOTCRAFT_GAME_VERSION` - Minecraft version to target (default: latest)
- `BOTCRAFT_COMPRESSION` - Enable zlib compression (ON)
- `BOTCRAFT_ENCRYPTION` - Enable OpenSSL encryption for online mode (ON)
- `BOTCRAFT_USE_OPENGL_GUI` - Enable OpenGL renderer (OFF in Replit)
- `BOTCRAFT_BUILD_EXAMPLES` - Build example bots (ON)
- `BOTCRAFT_BUILD_DOC` - Build Doxygen documentation (OFF)

## Building

The project is pre-configured and built. To rebuild from scratch:

```bash
mkdir -p build && cd build
cmake -DBOTCRAFT_GAME_VERSION=latest \
  -DCMAKE_BUILD_TYPE=Release \
  -DBOTCRAFT_BUILD_EXAMPLES=ON \
  -DBOTCRAFT_BUILD_TESTS=OFF \
  -DBOTCRAFT_COMPRESSION=ON \
  -DBOTCRAFT_ENCRYPTION=ON \
  -DBOTCRAFT_USE_OPENGL_GUI=OFF \
  -DBOTCRAFT_BUILD_DOC=OFF \
  -DZLIB_ROOT=/nix/store/rxd3kdsc7k72198g58wk0qp3xdip5x5d-zlib-1.3.1-dev \
  -DZLIB_LIBRARY=/nix/store/jl19fdc7gdxqz9a1s368r9d15vpirnqy-zlib-1.3.1/lib/libz.so \
  -DOPENSSL_ROOT_DIR=/nix/store/0225zs9jl9s2y3ai1arbg0h9z7z4bmfi-openssl-3.4.1-dev \
  -DOPENSSL_LIBRARIES="/nix/store/5xmcl9wr18g6ym3dh3363hv8hp6jyxqd-openssl-3.4.1/lib" \
  ..
cmake --build . --config Release --parallel 4
```

## Built Artifacts (in bin/)

- `libprotocolCraft.so` - Minecraft protocol library
- `libbotcraft.so` - Main botcraft library
- `0_HelloWorld` - Connect, say "Hello World!", disconnect
- `1_UserControlledExample` - Keyboard/mouse controlled bot
- `2_ChatCommandExample` - Chat-commanded bot
- `3_SimpleAFKExample` - Lightweight AFK bot
- `4_MapCreatorExample` - Autonomous pixel art builder
- `5_MobHitterExample` - Entity attack example
- `6_DispenserFarmExample` - Autonomous farm bot
- `7_WorldEaterExample` - Autonomous world eater bot

## Dependencies

System packages installed via Nix:
- `cmake` - Build system
- `gcc` / `g++` - C++17 compiler (GCC 14)
- `gnumake` - Build tool
- `zlib` - Compression support
- `openssl` - Encryption for online mode authentication

Local dependency (pre-downloaded):
- `3rdparty/asio/` - Asio networking library (header-only)
- `3rdparty/zlib/` - zlib source (used if system not found)
- `3rdparty/openssl/` - openssl-cmake source (used if system not found)

## User Preferences

- OpenGL GUI disabled (headless environment)
- Tests disabled by default
- Documentation generation disabled
