package com.gfx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NetworkOptimizer {
    
    /**
     * [PT-BR] Aplica otimizações de rede TCP e DNS via Shizuku.
     * Requer privilégios de sistema para alterar sysctl ou propriedades de rede.
     */
    suspend fun applyOptimizations(enabled: Boolean): String = withContext(Dispatchers.IO) {
        if (!ShizukuHelper.hasPermission()) return@withContext "Shizuku não autorizado."
        
        try {
            if (enabled) {
                // Aumentar buffers TCP para melhor throughput e menos perda
                ShellFunctions.executePrivileged("sysctl -w net.core.rmem_max=2621440")
                ShellFunctions.executePrivileged("sysctl -w net.core.wmem_max=2621440")
                ShellFunctions.executePrivileged("sysctl -w net.ipv4.tcp_rmem='4096 87380 2621440'")
                ShellFunctions.executePrivileged("sysctl -w net.ipv4.tcp_wmem='4096 16384 2621440'")
                
                // Forçar DNS do Cloudflare (1.1.1.1) para menor latência de resolução
                ShellFunctions.executePrivileged("settings put global dns1 1.1.1.1")
                ShellFunctions.executePrivileged("settings put global dns2 1.0.0.1")
                
                "Otimizações de rede aplicadas (TCP Buffers + Cloudflare DNS)."
            } else {
                // Restaurar padrão
                ShellFunctions.executePrivileged("settings put global dns1 ''")
                ShellFunctions.executePrivileged("settings put global dns2 ''")
                "Otimizações desativadas e DNS restaurado."
            }
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }
}
