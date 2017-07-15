package de.teamalbin.spigotmc.easyportals;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerInteraction implements Listener, CommandExecutor, TabCompleter {
    private PortalManager portals;

    public PlayerInteraction(PortalManager portals) {
        this.portals = portals;
    }

    private List<Block> blocksAround(Block b) {
        return Arrays.asList(b,
                b.getRelative(-1, 0, 0), b.getRelative(1, 0, 0),
                b.getRelative(0, -1, 0), b.getRelative(0, 1, 0),
                b.getRelative(0, 0, -1), b.getRelative(0, 0, 1),
                b.getRelative(-1, 1, 0), b.getRelative(1, 1, 0),
                b.getRelative(0, 1, -1), b.getRelative(0, 1, 1),
                b.getRelative(-1, -1, 0), b.getRelative(1, -1, 0),
                b.getRelative(0, -1, -1), b.getRelative(0, -1, 1));
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // don't spawn baddies from our portals
        if (event.isCancelled()) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NETHER_PORTAL) return;
        if (portals.manages(event.getLocation().getBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        // prevent automatic despawning of portal blocks that belong to our portals
        if (event.isCancelled()) return;
        if (portals.manages(event)) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.isCancelled()) return;
        if (portals.manages(event.getEntity().getLocation().getBlock())) event.setCancelled(true);
    }

    // prevent players who have recently touched a portal from entering the nether by mistake
    private HashMap<String, Instant> netherProtect = new HashMap<>();

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) return;
        if (netherProtect.containsKey(event.getPlayer().getName()) &&
                netherProtect.get(event.getPlayer().getName()).isAfter(Instant.now())) {
            event.setCancelled(true);
            return;
        }
        if (portals.manages(blocksAround(event.getFrom().getBlock()), event.getFrom().getBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityEnterPortal(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Portal portal = portals.findPortalFor(event.getLocation().getBlock());
        if (portal != null) {
            netherProtect.put(player.getName(), Instant.now().plusSeconds(7));
            portal.teleport(player);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (portals.manages(blocksAround(b), b)) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block b = event.getBlock();
        if (portals.manages(blocksAround(b), b)) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockExplode(EntityExplodeEvent event) {
        if (portals.manages(event.blockList(), event.getLocation().getBlock())) event.setCancelled(true);
    }

    private static final List<String> completableCommands = Arrays.asList("create", "list", "link", "flip", "visit", "unlink", "destroy");

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] strings) {
        if (!command.getName().equals("portal")) return null;
        if (!commandSender.hasPermission("easyportals.build")) return null;
        if (strings.length == 1) return PlayerInteraction.completableCommands.stream()
                .filter(cp -> cp.startsWith(strings[0])).collect(Collectors.toList());
        if (strings.length > 1) {
            ArrayList<String> portalNames = this.portals.getNames();
            if (strings.length == 2) return portalNames.stream().filter(cp -> cp.startsWith(strings[1])).collect(Collectors.toList());
            portalNames.add("random");
            portalNames.add("here");
            if (strings.length == 3) return portalNames.stream().filter(cp -> cp.startsWith(strings[2])).collect(Collectors.toList());
        }
        return null;
    }

    private boolean mayPerform(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        player.sendMessage(ChatColor.RED + "Sorry, you may not perform that command. Missing permission: " + ChatColor.RESET + permission);
        return false;
    }

    private boolean checkError(Player player, PortalManager.PortalManagerError pme) {
        if (pme == null) return true;
        player.sendMessage(ChatColor.RED + "Error: " + ChatColor.RESET + pme.getMessage());
        return true;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) return false;
        Player player = (Player) commandSender;
        if (!command.getName().equals("portal")) return false;
        if (strings.length == 0) {
            return false;
        }
        switch (strings[0].toLowerCase()) {
            case "create": if (mayPerform(player, "easyportals.build")) {
                if (strings.length != 2) {
                    player.sendMessage("Please give the portal a name: /portal create <name>.");
                    return true;
                }
                return checkError(player, this.portals.createPortalNear(player, strings[1]));
            } else return true;
            case "link": if (mayPerform(player, "easyportals.build")) {
                if (strings.length != 3) {
                    player.sendMessage("What do you want to link? Try /portal link <p1> <p2>, or /portal link <p1> random.");
                    return true;
                }
                return checkError(player, this.portals.linkPortals(player, strings[1], strings[2]));
            } else return true;
            case "flip": if (mayPerform(player, "easyportals.build")) {
                if (strings.length != 2) {
                    player.sendMessage("Which portal do you want to flip? /portal visit <name>");
                    return true;
                }
                return checkError(player, this.portals.flipPortal(player, strings[1]));
            } else return true;
            case "visit": if (mayPerform(player, "easyportals.build")) {
                if (strings.length != 2) {
                    player.sendMessage("Which portal do you want to visit? /portal visit <name>");
                    return true;
                }
                return checkError(player, this.portals.visitPortal(player, strings[1]));
            } else return true;
            case "unlink": if (mayPerform(player, "easyportals.build")) {
                if (strings.length != 2) {
                    player.sendMessage("Which portal do you want to unlink? /portal unlink <name>");
                    return true;
                }
                return checkError(player, this.portals.unlinkPortal(player, strings[1]));
            } else return true;
            case "destroy": if (mayPerform(player, "easyportals.build")) {
                if (strings.length != 2) {
                    player.sendMessage("Which portal do you want to destroy? /portal destroy <name>");
                    return true;
                }
                return checkError(player, this.portals.destroyPortal(player, strings[1]));
            } else return true;
            case "list": if (mayPerform(player, "easyportals.build")) {
                return checkError(player, this.portals.list(player));
            } else return true;
        }
        player.sendMessage(ChatColor.RED + "Sorry, that's not a known command.");
        return false;
    }
}
