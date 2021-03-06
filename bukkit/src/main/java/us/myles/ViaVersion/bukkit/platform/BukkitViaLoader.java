package us.myles.ViaVersion.bukkit.platform;

import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import us.myles.ViaVersion.ViaVersionPlugin;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.data.UserConnection;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.platform.ViaPlatformLoader;
import us.myles.ViaVersion.bukkit.listeners.UpdateListener;
import us.myles.ViaVersion.bukkit.listeners.protocol1_9to1_8.*;
import us.myles.ViaVersion.bukkit.providers.BukkitViaBulkChunkTranslator;
import us.myles.ViaVersion.bukkit.providers.BukkitViaMovementTransmitter;
import us.myles.ViaVersion.protocols.base.ProtocolInfo;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.BulkChunkTranslatorProvider;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.HandItemProvider;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class BukkitViaLoader implements ViaPlatformLoader {
    private ViaVersionPlugin plugin;

    @Override
    public void load() {
        // Update Listener
        Bukkit.getPluginManager().registerEvents(new UpdateListener(), plugin);

        /* Base Protocol */
        final ViaVersionPlugin plugin = (ViaVersionPlugin) Bukkit.getPluginManager().getPlugin("ViaVersion");

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent e) {
                Via.getManager().removePortedClient(e.getPlayer().getUniqueId());
            }
        }, plugin);

        /* 1.9 client to 1.8 server */

        new ArmorListener(plugin).register();
        new DeathListener(plugin).register();
        new BlockListener(plugin).register();

        if (Bukkit.getVersion().toLowerCase().contains("paper") || Bukkit.getVersion().toLowerCase().contains("taco")) {
            plugin.getLogger().info("Enabling PaperSpigot/TacoSpigot patch: Fixes block placement.");
            new PaperPatch(plugin).register();
        }
        if (plugin.getConf().isItemCache()) {
            new HandItemCache().runTaskTimerAsynchronously(plugin, 2L, 2L); // Updates player's items :)
            HandItemCache.CACHE = true;
        }

        /* Providers */
        Via.getManager().getProviders().use(BulkChunkTranslatorProvider.class, new BukkitViaBulkChunkTranslator());
        Via.getManager().getProviders().use(MovementTransmitterProvider.class, new BukkitViaMovementTransmitter());
        Via.getManager().getProviders().use(HandItemProvider.class, new HandItemProvider() {
            @Override
            public Item getHandItem(final UserConnection info) {
                if (HandItemCache.CACHE) {
                    return HandItemCache.getHandItem(info.get(ProtocolInfo.class).getUuid());
                } else {
                    try {
                        return Bukkit.getScheduler().callSyncMethod(Bukkit.getPluginManager().getPlugin("ViaVersion"), new Callable<Item>() {
                            @Override
                            public Item call() throws Exception {
                                UUID playerUUID = info.get(ProtocolInfo.class).getUuid();
                                if (Bukkit.getPlayer(playerUUID) != null) {
                                    return HandItemCache.convert(Bukkit.getPlayer(playerUUID).getItemInHand());
                                }
                                return null;
                            }
                        }).get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        Via.getPlatform().getLogger().severe("Error fetching hand item: " + e.getClass().getName());
                        if (Via.getManager().isDebug())
                            e.printStackTrace();
                        return null;
                    }
                }
            }
        });

    }
}
