package com.demonica.celeritas.guard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * In a development environment, stops the game when a quarantine injector found no target ({@link QuarantinePlugin}),
 * so a pin move cannot quietly lose a patch. The audit cannot stop it itself: Mixin downgrades an exception from the
 * config plugin to a warning (the quarantine is not required), and a class that fails in a transformer extension may
 * be one Celeritas loads on a chunk-builder thread, which logs the error and carries on. So the misses are checked on
 * the game thread: once at init, for the classes transformed before it, and on every client tick, for those
 * transformed when a world loads. Forge's event bus rethrows a listener's exception, and the client stops with a crash
 * report. Production never registers this.
 */
public final class InjectionAuditDevCheck {
    private InjectionAuditDevCheck() {
    }

    /** Checks now, and on every client tick from here on. */
    public static void register() {
        check();
        MinecraftForge.EVENT_BUS.register(InjectionAuditDevCheck.class);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        check();
    }

    private static void check() {
        List<String> missed = QuarantinePlugin.missedInjectors();
        if (!missed.isEmpty()) {
            throw new IllegalStateException("Demonica's Celeritas patches found no target; a pin move lost a patch: " + missed);
        }
    }
}
