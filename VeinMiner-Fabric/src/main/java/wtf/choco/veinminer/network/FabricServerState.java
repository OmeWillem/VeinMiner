package wtf.choco.veinminer.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.NotNull;

import wtf.choco.veinminer.VeinMiner;
import wtf.choco.veinminer.network.protocol.ClientboundPluginMessageListener;
import wtf.choco.veinminer.network.protocol.clientbound.PluginMessageClientboundHandshakeResponse;
import wtf.choco.veinminer.network.protocol.clientbound.PluginMessageClientboundSetPattern;
import wtf.choco.veinminer.network.protocol.clientbound.PluginMessageClientboundSyncRegisteredPatterns;
import wtf.choco.veinminer.network.protocol.clientbound.PluginMessageClientboundVeinMineResults;
import wtf.choco.veinminer.network.protocol.serverbound.PluginMessageServerboundSelectPattern;
import wtf.choco.veinminer.util.NamespacedKey;

/**
 * The client's state on a connected server.
 */
public final class FabricServerState implements ClientboundPluginMessageListener, MessageReceiver {

    private boolean enabled, active;

    private int selectedPatternIndex = 0;
    private List<NamespacedKey> patternKeys = null;

    /**
     * Construct a new {@link FabricServerState}.
     *
     * @param client the {@link MinecraftClient} instance
     */
    public FabricServerState(@NotNull MinecraftClient client) {
        boolean dev = FabricLoader.getInstance().isDevelopmentEnvironment();

        // We'll enable VeinMiner if we're in singleplayer development mode, just for testing
        this.enabled = client.isInSingleplayer() && dev;

        if (dev) { // Debug patterns
            this.patternKeys = List.of(
                NamespacedKey.veinminer("default"),
                NamespacedKey.veinminer("tunnel"),
                NamespacedKey.veinminer("staircase_up"),
                NamespacedKey.veinminer("staircase_down")
            );
        }
    }

    /**
     * Check whether or not vein miner is enabled on the client.
     * <p>
     * If this method returns {@code false}, this means that the server has not allowed the
     * client to activate vein miner using a key bind, and therefore the client should not be
     * sending messages to the server claiming that it has been activated or deactivated, or
     * perform any other client-sided functionality.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set whether or not vein miner is active.
     *
     * @param active the new active state
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Check whether or not vein mine is active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Get the index of the currently selected pattern.
     *
     * @return the selected pattern index
     */
    public int getSelectedPatternIndex() {
        return selectedPatternIndex;
    }

    @NotNull
    public NamespacedKey getSelectedPattern() {
        return patternKeys.get(selectedPatternIndex);
    }

    @NotNull
    public NamespacedKey getNextPattern() {
        return patternKeys.get((selectedPatternIndex + 1) % patternKeys.size());
    }

    @NotNull
    public NamespacedKey getPreviousPattern() {
        int index = (selectedPatternIndex - 1) % patternKeys.size();

        if (index < 0) {
            index = patternKeys.size() + index;
        }

        return patternKeys.get(index);
    }

    /**
     * Change to an adjacent pattern.
     *
     * @param next whether or not to move to the next or previous index
     *
     * @return true if the pattern was changed, false if the client does not recognize any
     * pattern yet and could not be changed
     */
    public boolean changePattern(boolean next) {
        if (patternKeys == null) {
            return false;
        }

        this.selectedPatternIndex += (next ? 1 : -1);
        this.selectedPatternIndex %= patternKeys.size();

        if (selectedPatternIndex < 0) {
            this.selectedPatternIndex = patternKeys.size() + selectedPatternIndex;
        }

        VeinMiner.PROTOCOL.sendMessageToServer(this, new PluginMessageServerboundSelectPattern(patternKeys.get(selectedPatternIndex)));

        return true;
    }

    /**
     * Get a {@link List} of all pattern keys known to the client.
     *
     * @return all known pattern keys
     */
    @NotNull
    public List<NamespacedKey> getPatternKeys() {
        return (patternKeys != null) ? patternKeys : Collections.emptyList();
    }

    public boolean hasPatternKeys() {
        return patternKeys != null && !patternKeys.isEmpty();
    }

    @Override
    public void sendMessage(@NotNull NamespacedKey channel, byte[] message) {
        PacketByteBuf byteBuf = PacketByteBufs.create();
        byteBuf.writeBytes(message);

        ClientPlayNetworking.send(new Identifier(channel.namespace(), channel.key()), byteBuf);
    }

    @Override
    public void handleHandshakeResponse(@NotNull PluginMessageClientboundHandshakeResponse message) {
        this.enabled = message.isEnabled();
    }

    @Override
    public void handleSyncRegisteredPatterns(@NotNull PluginMessageClientboundSyncRegisteredPatterns message) {
        boolean firstSync = (patternKeys == null);
        NamespacedKey previouslySelectedPatternKey = (!firstSync && selectedPatternIndex < patternKeys.size()) ? patternKeys.get(selectedPatternIndex) : null;

        this.patternKeys = new ArrayList<>(message.getKeys());

        // Reselect the index that was previously selected (if it changed), or default to the first if it does not exist anymore
        if (!firstSync && previouslySelectedPatternKey != null) {
            this.selectedPatternIndex = Math.max(patternKeys.indexOf(previouslySelectedPatternKey), 0);
        }
    }

    @Override
    public void handleVeinMineResults(@NotNull PluginMessageClientboundVeinMineResults message) {
        // TODO: Handle the results
    }

    @Override
    public void handleSetPattern(@NotNull PluginMessageClientboundSetPattern message) {
        this.selectedPatternIndex = Math.max(patternKeys.indexOf(message.getPatternKey()), 0);
    }

}
