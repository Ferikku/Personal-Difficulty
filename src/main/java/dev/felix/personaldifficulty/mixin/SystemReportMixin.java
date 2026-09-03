package dev.felix.personaldifficulty.mixin;

import net.minecraft.SystemReport;
import oshi.software.os.OperatingSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the OS process-detection section of the crash report on Windows. The default
 * {@code putSoftware} lambda walks all running processes to enrich the report, which can be slow
 * and noisy to print; on Windows we cancel that part to keep the report generation fast and clean.
 */
@Mixin(SystemReport.class)
public abstract class SystemReportMixin {
    @Inject(method = "lambda$putSoftware$0", at = @At("HEAD"), cancellable = true)
    private void personaldifficulty$skipProcessDetailsOnWindows(OperatingSystem operatingSystem, CallbackInfo ci) {
        String osName = System.getProperty("os.name", "");
        if (osName.toLowerCase().contains("win")) {
            ci.cancel();
        }
    }
}
