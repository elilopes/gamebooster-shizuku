# GFX Game Booster & System Optimizer

[English](#english) | [Português (Brasil)](#português-brasil)

---

## English

**GFX Game Booster & System Optimizer** is an advanced Android application built with Kotlin and Jetpack Compose designed to elevate gaming performance, manage system privileges (via Shizuku / Root), monitor hardware telemetry, and provide powerful diagnostic and installation tools.

### 🚀 Key Features

1. **Game List & Management (Room Database)**
   - Persist games using Room database with instantaneous search and background initialization.
   - Manual addition and one-tap auto-scan of installed games and apps.
   - Web update checking across repositories (APKMirror, Google Play, F-Droid).

2. **Detailed APK Installer & PackageManager Live Logcat**
   - Step-by-step installation monitoring with live logcat for `PackageManager` and `PackageInstaller`.
   - Clear, user-friendly success and error reporting with full command visibility (`pm install-create`, `pm install-write`, `pm install-commit`).
   - Reinstall/detailed install integration directly from the app backup manager.

3. **Network Latency & TCP Ping Monitor + Optimizer**
   - Real-time TCP socket probing (Cloudflare, Google DNS, Steam, AWS, Riot) to measure Ping, Jitter, and Packet Loss.
   - TCP buffer tuning and Cloudflare DNS (1.1.1.1) forced routing to reduce latency and improve online gaming stability.

4. **Advanced Boost Settings & Performance Tweaks**
   - **Thermal Throttling Control**: Prevent CPU/GPU frequency scaling under high temperatures.
   - **Hardware Overlays**: Disable hardware overlays for direct GPU rendering access.
   - **OOM Killer Protection**: Protect active game processes from background termination.
   - **CPU / GPU Max Frequency**: Lock CPU and GPU cores at maximum performance states.
   - **Native Game Mode**: Activate Android OS Core Game Mode (`cmd game`).
   - **Notification & Logger Control**: Block heads-up notifications and reduce system logger buffer overhead.

5. **App Backup & Restore Manager**
   - Full backup and restore support for APK, OBB folders, and App Data.
   - Compression support and Storage Access Framework (SAF) export.

6. **Bloatware Manager**
   - Detect and uninstall pre-installed system bloatware packages safely.

7. **Custom Shell Scripts (.sh) & Terminal**
   - Create, edit, and execute custom shell scripts with Shizuku / Root privileges.
   - Pre-configured performance scripts (120Hz mode, RAM trimming, Skia Vulkan renderer).

8. **SetEdit (Settings Provider Editor)**
   - Modify global, system, and secure Android table variables without root using Shizuku.

9. **FPS Analytics & A/B Benchmarking**
   - Real-time framestats extraction via `dumpsys gfxinfo`.
   - Calculate Average FPS, 1% Low FPS, and Jank percentage.
   - Side-by-side A/B profile comparison to evaluate optimization efficacy.

---

## Português (Brasil)

O **GFX Game Booster & System Optimizer** é um aplicativo avançado para Android desenvolvido em Kotlin e Jetpack Compose, projetado para elevar o desempenho em jogos, gerenciar privilégios de sistema (via Shizuku / Root), monitorar telemetria de hardware e fornecer ferramentas poderosas de diagnóstico e instalação.

### 🚀 Principais Funcionalidades

1. **Lista de Jogos e Gerenciamento (Banco de Dados Room)**
   - Persistência de jogos utilizando o banco de dados Room com busca instantânea e carregamento em segundo plano.
   - Adição manual e escaneamento automático (auto-scan) de jogos e aplicativos instalados.
   - Verificação de atualizações web em repositórios (APKMirror, Google Play, F-Droid).

2. **Instalador Detalhado de APKs e Logcat ao Vivo do PackageManager**
   - Monitoramento passo a passo da instalação com logcat em tempo real para `PackageManager` e `PackageInstaller`.
   - Mensagens de sucesso e erro amigáveis ao usuário, exibindo os comandos executados (`pm install-create`, `pm install-write`, `pm install-commit`).
   - Integração direta de reinstalação e instalação detalhada a partir do gerenciador de backups.

3. **Monitor de Latência TCP / Ping e Otimizador de Rede**
   - Testes de conexão TCP em tempo real (Cloudflare, Google DNS, Steam, AWS, Riot) para medir Ping, Jitter e Perda de Pacotes.
   - Ajuste de buffers TCP e forçamento de DNS da Cloudflare (1.1.1.1) para menor latência e maior estabilidade em jogos online.

4. **Configurações Avançadas de Boost e Desempenho**
   - **Controle Térmico**: Desativa o throttling térmico para evitar queda de velocidade na CPU/GPU.
   - **Desativação de Overlays**: Dá acesso direto à GPU para renderização mais rápida.
   - **Proteção contra OOM**: Protege o processo do jogo contra encerramento pelo sistema.
   - **Frequência Máxima de CPU/GPU**: Trava os núcleos no desempenho máximo.
   - **Modo de Jogo Nativo**: Ativa o Game Mode nativo do Android OS (`cmd game`).
   - **Bloqueio de Notificações e Logger**: Oculta notificações flutuantes e reduz o buffer do logcat.

5. **Gerenciador de Backup e Restauração de Apps**
   - Suporte completo para backup e restauração de APK, pastas OBB e Dados do aplicativo.
   - Compactação em ZIP e exportação via Storage Access Framework (SAF).

6. **Gerenciador de Bloatware**
   - Detecta e remove aplicativos pré-instalados indesejados (bloatware) com segurança.

7. **Scripts Shell Personalizados (.sh) e Terminal**
   - Crie, edite e execute scripts shell com privilégios Shizuku / Root.
   - Atalhos pré-configurados (Modo 120Hz, limpeza de RAM, renderizador Skia Vulkan).

8. **SetEdit (Editor de Configurações do Sistema)**
   - Modifique variáveis globais, de sistema e seguras das tabelas do Android sem root via Shizuku.

9. **Métricas de FPS e Comparação de Benchmarks A/B**
   - Extração de estatísticas de quadros em tempo real via `dumpsys gfxinfo`.
   - Cálculo de FPS Médio, 1% Low FPS e porcentagem de engasgos (Jank).
   - Comparação lado a lado (A/B) para avaliar a eficácia das otimizações.
