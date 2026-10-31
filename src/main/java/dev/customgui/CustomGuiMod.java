package dev.customgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class CustomGuiMod implements ClientModInitializer {

    /** Der Chat schließt sich nach dem Befehl selbst und würde unseren Screen überschreiben,
     *  deshalb öffnen wir das GUI erst im nächsten Client-Tick. */
    private static boolean pendingOpen = false;

    @Override
    public void onInitializeClient() {
        CustomGuiConfig.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("gui").executes(ctx -> {
                    pendingOpen = true;
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pendingOpen) {
                pendingOpen = false;
                mc.setScreen(new CustomGuiScreen());
            }
        });
    }
}
