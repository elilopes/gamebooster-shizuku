# GFX Game Booster Android

## English

GFX Game Booster is an advanced Android utility and performance optimization tool designed to maximize gaming performance, manage system resources, fine-tune display configurations, and safely manage game installations and backups.

### Key Features

* **System State Recovery & Notification Panic Button:**
    * **State Persistence:** Automatically records original screen resolution (`wm size`), density (`wm density`), animation scales, private DNS, and DND settings whenever system tweaks are applied.
    * **Auto-Recovery on App Launch / Game Exit:** If the app is killed or the device restarts while tweaks are active, original settings are automatically restored when opening the app or leaving a game.
    * **Quick Panic Button in Notification Bar:** Features a 1-tap `🚨 Restore (Panic)` action directly in the persistent notification to instantly reset resolution, restore animations, revert DNS, disable DND, and safely terminate active game processes.

* **Apply & Verify Engine (Real Settings & Properties Verification):**
    * Every `settings put` or `setprop` command is immediately read back with `settings get` / `getprop` to verify whether the system actually accepted the modification.
    * Clearly displays **"Applied and verified"** or **"No effect on device (read back: X)"** instead of false success messages.

* **Hardware & ROM Feature Detection:**
    * Automatically detects vendor-specific keys (such as `touch_response_rate`, `gaming_touch_mode`, `game_touch_enhancement`).
    * Toggles for features not supported by the device's hardware or ROM are cleanly hidden.

* **Network-Safe Background Suspension (Online Gaming Protection):**
    * `cmd deviceidle force-idle` was removed prior to launching games to prevent aggressive Android Doze modes from throttling network sockets, ping, or dropping online game connections. Background tasks are suspended cleanly while keeping Wi-Fi/mobile data fully responsive (`cmd deviceidle unforce`).

* **Accurate CPU & GPU Governor Diagnostics:**
    * Validates whether `/sys/devices/system/cpu/.../scaling_governor` and `/sys/class/kgsl/...` nodes exist on the kernel.
    * Catches real SELinux and kernel permissions errors when Shizuku lacks root permissions, displaying the actual diagnostic message rather than falsely reporting "activated".

* **Android 13+ Game Mode: FPS Cap & Downscale Overlays:**
    * Fine-tune FPS caps (30 FPS, 45 FPS, 60 FPS, 90 FPS, 120 FPS, or Unlimited) alongside resolution downscale factors (`1.0x` down to `0.5x`).
    * Stabilizing FPS at 30 or 45 FPS delivers superior frametime consistency compared to an oscillating 60 FPS.
    * Utilizes `device_config put game_overlay` and `cmd game mode custom`.

* **Real A/B Profile Benchmarking (`dumpsys gfxinfo framestats`):**
    * Records real frametime metrics directly from Android Choreographer and RenderThread.
    * Calculates **Average FPS**, **1% Low FPS** (99th percentile frame latency), and **Jank %**.
    * **A/B Comparison Mode:** Compare Profile A (Stock) against Profile B (Optimized) side-by-side to objectively verify whether a configuration improved game stability.

* **Do Not Disturb (DND) Gaming Shield:**
    * Blocks heads-up notifications, floating pop-ups, and overlay intrusions during gameplay using `NotificationManager.setInterruptionFilter`, `zen_mode 1`, and `heads_up_notifications_enabled 0`.

* **TCP Network Latency & Jitter Monitor (Privilege-Free):**
    * Optional menu toggle to monitor real-time network latency (ping RTT in ms) and packet jitter via standard TCP socket probing without requiring root or special permissions.
    * Identifies whether in-game stutter or lag is caused by local hardware or network route instability.

* **Detailed APK & Package Installer:**
    * Multi-format inspection and installation for `.apk`, `.apks`, `.xapk`, `.apkm`, and `.zip` backups.
    * Entry-by-entry unzip tracking with byte-level progress.
    * Full technical sheet: package name, app label, icon, `versionName`, `versionCode`, `minSdk`, `targetSdk`, and supported ABIs.
    * Anti-Tamper and DRM detection (Denuvo, SecuROM, Tencent, NetEase, VMProtect, etc.).
    * `UPDATE_INCOMPATIBLE` signature mismatch warning with 1-tap pre-installation backup.
    * Advanced installation flags: `-d` (allow downgrade), `-t` (allow test packages), and `-g` (grant runtime permissions).
    * Silent Shizuku/Root session installation (`pm install-create`, `install-write`, `install-commit`) with human-readable error diagnostics and system fallback installer.
    * Live logcat console filtered by `PackageManager`, `PackageInstaller`, and package name.

* **Game Backup & Restore (with SAF):**
    * Full backup of APK (including splits), OBB directories, and user data.
    * Timestamp tracking on every backup.
    * Selective restoration (APK only, OBB only, Data only, or full restore).
    * External export via Storage Access Framework (SAF).

* **Multi-Language Support:**
    * English, Português (BR), Русский, 한국어, 中文, हिन्दी, and Français.

### Requirements
Requires root privileges or [Shizuku](https://rikka.app/shizuku/) installed and authorized on your device.

---

## Português (Brasil)

O GFX Game Booster é uma ferramenta avançada de utilitários e otimização de desempenho para Android, desenvolvida para extrair a máxima performance em jogos, gerenciar recursos de hardware, ajustar resoluções com precisão e realizar instalações e backups seguros de jogos e aplicativos.

### Principais Funcionalidades

* **Recuperação de Estado do Sistema e Botão de Pânico na Notificação:**
    * **Persistência de Estado:** Registra automaticamente a resolução original da tela (`wm size`), densidade (`wm density`), escalas de animação, DNS Privado e configurações de Não Perturbe antes de qualquer ajuste.
    * **Restauração Automática ao Abrir o App / Sair do Jogo:** Caso o aplicativo seja fechado ou o aparelho reinicie com alterações ativas, o estado original é restaurado com segurança.
    * **Botão de Pânico na Barra de Notificação:** Botão de 1 toque `🚨 Restaurar (Pânico)` na notificação persistente para resetar resolução, restabelecer animações para 1.0x, reverter DNS, desativar DND e encerrar o jogo ativo instantaneamente.

* **Motor "Aplicar e Verificar" (Conferência Real de Leitura):**
    * Cada comando `settings put` ou `setprop` é imediatamente verificado com `settings get` / `getprop` para conferir se o sistema realmente aceitou a alteração.
    * Exibe **"Aplicado e verificado"** ou **"Sem efeito no dispositivo (lido de volta: X)"** em vez de emitir avisos falsos de sucesso.

* **Detecção Inteligente de Hardware e ROM:**
    * Detecta automaticamente chaves proprietárias de fabricantes (como `touch_response_rate`, `gaming_touch_mode`, `game_touch_enhancement`).
    * Oculta da tela toggles e opções cujas chaves não existam no aparelho do usuário.

* **Suspensão Segura de Rede (Proteção para Jogos Online):**
    * O comando `cmd deviceidle force-idle` foi revisado e removido antes da abertura de jogos para evitar que o modo Doze corte o tráfego de rede, conexões de sockets ou gere desync/desconexões em partidas online. A rede permanece 100% desimpedida (`cmd deviceidle unforce`).

* **Diagnóstico Real de Governors de CPU e GPU:**
    * Verifica se os nós `/sys/devices/system/cpu/.../scaling_governor` e `/sys/class/kgsl/...` existem no kernel.
    * Captura erros reais de permissão e bloqueios de SELinux quando o Shizuku não possui permissão de root para alterar o governor, alertando o usuário com a mensagem real do sistema.

* **Game Mode Android 13+: Teto de FPS e Downscale:**
    * Ajuste de teto de FPS fixo (30 FPS, 45 FPS, 60 FPS, 90 FPS, 120 FPS ou Ilimitado) combinado com fator de downscale (`1.0x` a `0.5x`).
    * Fixar o FPS em 30 ou 45 estáveis proporciona tempos de quadro (frametimes) muito mais consistentes do que 60 FPS oscilando.
    * Configurado via `device_config put game_overlay` e `cmd game mode custom`.

* **Medição Real e Comparador A/B de Perfis (`dumpsys gfxinfo framestats`):**
    * Gravação de métricas reais direto do subsistema gráfico do Android.
    * Cálculo de **FPS Médio**, **1% Low FPS** (percentil 99 dos tempos de quadro) e **Jank %**.
    * **Comparador A/B:** Compare o Perfil A (Padrão) contra o Perfil B (Otimizado) lado a lado com veredito objetivo comprovando se o ajuste gerou ganhos reais de estabilidade.

* **Modo Não Perturbe (DND) durante o Jogo:**
    * Silencia pop-ups flutuantes, notificações heads-up e sobreposições durante a gameplay utilizando `NotificationManager.setInterruptionFilter`, `zen_mode 1` e `heads_up_notifications_enabled 0`.

* **Monitor de Latência e Jitter de Rede TCP (Sem Privilégios):**
    * Função opcional no menu para monitorar em tempo real ping TCP (ms) e oscilação de rota (jitter) até servidores de jogos (Cloudflare, Google, Valve/Steam, AWS, etc.).
    * Auxilia o jogador a identificar se o lag durante as partidas é decorrente de instabilidade de internet ou gargalo de hardware.

* **Instalador de APKs e Pacotes Detalhado:**
    * Suporte a `.apk`, `.apks`, `.xapk`, `.apkm` e backups `.zip`.
    * Descompactação monitorada entrada por entrada via `ZipInputStream` com progresso de bytes.
    * Ficha técnica completa: pacote, nome, ícone, `versionName`, `versionCode`, `minSdk`, `targetSdk` e ABIs.
    * Detecção de bibliotecas Anti-Tamper e DRM (Denuvo, SecuROM, Tencent, NetEase, VMProtect, etc.).
    * Alerta de `UPDATE_INCOMPATIBLE` com oferta de backup prévio antes da desinstalação.
    * Chaves avançadas: `-d` (permitir downgrade), `-t` (permitir pacotes de teste) e `-g` (conceder permissões).
    * Sessão de instalação privilegiada Shizuku/Root (`pm install-create`, `write`, `commit`) com diagnóstico de erros traduzido e instalador alternativo do sistema.
    * Console de logcat ao vivo filtrado por `PackageManager`, `PackageInstaller` e pelo pacote do aplicativo.

* **Backup e Restauração de Jogos (com SAF):**
    * Backup completo de APKs (incluindo splits), diretórios OBB e dados.
    * Registro de data e hora em cada backup.
    * Restauração seletiva (apenas APK, apenas OBB, apenas Dados ou completa).
    * Exportação externa para qualquer diretório via Storage Access Framework (SAF).

* **Suporte a Múltiplos Idiomas:**
    * Português (BR), English, Русский, 한국어, 中文, हिन्दी e Français.

### Requisitos
Requer permissões de Root ou o aplicativo [Shizuku](https://rikka.app/shizuku/) instalado e autorizado.
