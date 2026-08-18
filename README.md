<div align="center">
    <h1>Avrix API</h1>
    <p>
        Public API plugin for the <a href="https://github.com/Brov3r/Avrix">Avrix Loader</a> for Project Zomboid.
    </p>
</div>

<p align="center">
    <img alt="Project Zomboid Version" src="https://img.shields.io/badge/Project%20Zomboid-42.20.x%2B-blue">
    <img alt="Java Version" src="https://img.shields.io/badge/Java-25%2B-orange">
    <img alt="Environment" src="https://img.shields.io/badge/Environment-Client%20%7C%20Server-green">
    <a href="https://discord.gg/PdYtyJMTZN">
        <img alt="Discord" src="https://img.shields.io/discord/1248698287997976656?logo=discord&logoColor=%23ffffff&label=Discord&color=%235865F2">
    </a>
    <img alt="GitHub License" src="https://img.shields.io/github/license/Brov3r/Avrix-API">
</p>

## 📄 About

**Avrix API** is a public API plugin for the
[Avrix Loader](https://github.com/Brov3r/Avrix).

It provides shared interfaces, contracts, data structures, and services used by
Avrix plugins. The API is distributed separately from the loader core and must
be installed as a plugin before plugins that depend on it are started.

Avrix API does not launch Project Zomboid on its own. It requires the Avrix
Loader to be installed and used as the runtime environment.

## ✅ Requirements

- [Project Zomboid](https://store.steampowered.com/app/108600/Project_Zomboid/) `42.20.x+`
- [Avrix Loader](https://github.com/Brov3r/Avrix) `2.1.x+`
- Java Development Kit (JDK) `25+`

## 🚀 Installation

1. Download the latest `Avrix-API-<version>.jar` file from the
   [Releases](https://github.com/Brov3r/Avrix-API/releases) page.
2. Open the root directory of your Project Zomboid installation.
3. Create a `plugins` directory if it does not already exist.
4. Place the downloaded JAR file into the `plugins` directory.
5. Start Project Zomboid through the Avrix Loader.

Example layout:

```text
ProjectZomboid/
├── Avrix-Loader-<version>.jar
└── plugins/
    └── Avrix-API-<version>.jar
```

For a dedicated server without Steam integration, start the loader with the
`-nosteam` argument:

```bash
java -jar ./Avrix-Loader-<version>.jar -nosteam
```

## 🧩 Plugin Development

Add the Avrix API JAR to your plugin's compile-time dependencies.

With Gradle Groovy DSL:

```groovy
dependencies {
    compileOnly files('libs/Avrix-API-<version>.jar')
}
```

The API should normally be declared as `compileOnly`, because the runtime
instance is provided by Avrix Loader. Do not bundle `Avrix-API` into your plugin
JAR unless your deployment model explicitly requires it.

## 📚 Documentation

For loader and plugin development documentation, see the
[Avrix Wiki](https://github.com/Brov3r/Avrix/wiki).

## 🤝 Contributing

Contributions, bug reports, and suggestions are welcome.

Before opening a pull request:

1. Check the existing issues and pull requests.
2. Describe the purpose of your change.
3. Keep public API changes documented.
4. Verify that the project builds successfully with JDK 25 or higher.

## 💬 Community

Join the [Avrix Discord server](https://discord.gg/PdYtyJMTZN) to ask questions,
discuss development, and share feedback.

## ⚖️ License

This project is licensed under the [MIT License](./LICENSE).