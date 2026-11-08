<div align="center">
    <h1>Avrix API</h1>
</div>

<p align="center">
    <img alt="PZ Version" src="https://img.shields.io/badge/Project_Zomboid-b42+-blue">
    <img alt="Java Version" src="https://img.shields.io/badge/Java-25-orange">
    <a href="https://discord.gg/PdYtyJMTZN"><img alt="Discord" src="https://img.shields.io/discord/1248698287997976656?logo=discord&logoColor=%23ffffff&label=Discord&color=%235865F2"></a>
    <img alt="GitHub License" src="https://img.shields.io/github/license/Brov3r/Avrix-API">
</p>

**Avrix API** is the public API plugin for the [Avrix Loader](https://github.com/Brov3r/Avrix), providing shared
interfaces, contracts and services for Avrix plugins.

## 📚 Documentation

[📄 Open Documentation](./wiki/wiki-language.md "Avrix API Wiki")

## 🚀 Quick Start

1. Install the [Avrix Loader](https://github.com/Brov3r/Avrix) `2.4.0+`.
2. Download `Avrix-API-<version>.jar` from the [Releases Page](https://github.com/Brov3r/Avrix-API/releases).
3. Place it into the `plugins/` directory of your Project Zomboid installation.
4. Launch the game or dedicated server through the loader.

```bash
java -jar ./Avrix-Loader-<version>.jar
```

## 🧩 Plugin Development

Declare the API as `compileOnly` — the runtime instance is provided by the loader:

```groovy
dependencies {
    compileOnly files('libs/Avrix-API-<version>.jar')
}
```

## ⚖️ License

This project is licensed under the [MIT License](./LICENSE).
