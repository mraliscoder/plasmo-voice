package su.plo.voice.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.voice.client.VoiceClient;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow @Nullable public abstract ServerData getCurrentServer();

    @Shadow public abstract boolean isLocalServer();

    @Inject(at = @At("HEAD"), method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V")
    public void onDisconnect(Screen screen, CallbackInfo ci) {
        if (this.getCurrentServer() == null && !this.isLocalServer()) {
            return;
        }

        VoiceClient.LOGGER.info("Disconnect from " + (getCurrentServer() == null ? "localhost" : getCurrentServer().ip));

        VoiceClient.getClientConfig().save();
        VoiceClient.disconnect();
        VoiceClient.socketUDP = null;
    }
}
