package ca.carleton.sce.argus.jason;

import ca.carleton.sce.argus.Argus;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSyntax.Literal;
import net.citizensnpcs.api.ai.event.NavigationCancelEvent;
import net.citizensnpcs.api.ai.event.NavigationCompleteEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Wood;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

public class ArgusAgArch extends AgArch {
    private final Argus plugin;
    private final NPC npc;
    private final Inventory inv;
    private final Semaphore locationSemaphore;

    private boolean hasHouse;

    public static int WOOD_NEEDED_FOR_HOUSE = 30;
    public static int CHOP_WOOD_RADIUS = 5;
    public static int TREE_BROWSE_RADIUS = 100;
    public static int HOUSE_SIZE_IN_BLOCKS = 5;
    public static int INVENTORY_SIZE = 27;
    public static double REACHED_DESTINATION_THRESHOLD = 0;

    public ArgusAgArch(Argus plugin, NPC npc) {
        this.plugin = plugin;
        this.npc = npc;
        this.locationSemaphore = new Semaphore(1);
        this.inv = Bukkit.createInventory(null, INVENTORY_SIZE, this.getAgName() + " Inventory");
        this.hasHouse = false;
    }

    @Override
    public String getAgName() {
        return npc.getName();
    }

    @Override
    public List<Literal> perceive() {
        Entity ent = npc.getEntity();
        ArrayList<Literal> result = new ArrayList<>();
        ent.getWorld().spawnParticle(Particle.GLOW, ent.getLocation().add(0, 2, 0), 1, 0.2,
                0.2, 0.2, 0.1);
        result.add(Literal.parseLiteral("woodsChopped(" + getNumLogs() + ")"));

        if (findNearbyTree(CHOP_WOOD_RADIUS, true) != null) {
            result.add(Literal.parseLiteral("canSeeTree"));
        }

        if (hasHouse) {
            result.add(Literal.parseLiteral("hasHouse"));
        }
        return result;
    }

    @Override
    public void act(ActionExec action) {
        String actionName = action.getActionTerm().getFunctor();
        System.out.println("NPC " + npc.getName() + " performing action: " + actionName);
        switch (actionName) {
            case "say" -> {
                if (action.getActionTerm().getArity() == 1) {
                    String message = action.getActionTerm().getTerm(0).toString();
                    action.setResult(say(message));
                }
            }
            case "find_tree" -> action.setResult(findTree());
            case "chop_wood" -> action.setResult(chopWood());
            case "build_house" -> action.setResult(buildHouse());
            default -> System.out.println("Unknown action: " + actionName);
        }
        actionExecuted(action);
    }


    public void shutdown() {
        // Any cleanup if necessary
    }

    public boolean say(String message) {
        plugin.getServer().broadcastMessage("<" + getAgName() + "> " + message);
        return true;
    }

    public boolean findTree() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return false;
        }

        Block log = findNearbyTree(TREE_BROWSE_RADIUS, false);
        // Could not find wood!
        if (log == null) {
            return true;
        }

        // Pathfind towards the player entity (will repath as they move)
        goToLocation(log.getLocation());
        return true;
    }

    public boolean chopWood() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return false;
        }

        var ent = npc.getEntity();

        Block log = findNearbyTree(CHOP_WOOD_RADIUS, true);
        // Could not find wood!
        if (log == null) {
            return true;
        }

        log.breakNaturally();
        for (Entity e : ent.getWorld().getNearbyEntities(ent.getLocation(), CHOP_WOOD_RADIUS,
                CHOP_WOOD_RADIUS, CHOP_WOOD_RADIUS)) {
            if (e instanceof Item item && item.getItemStack().getType() == Material.OAK_LOG) {
                inv.addItem(item.getItemStack());
                item.remove();
                System.out.println("🪵 Picked up a log! Total log count is " + getNumLogs());
            }
        }
        return true;
    }

    public boolean buildHouse() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return false;
        }

        Entity ent = npc.getEntity();
        Location base = ent.getLocation().clone().add(2, 0, 0);
        World world = base.getWorld();

        // Floor
        for (int x = 0; x < HOUSE_SIZE_IN_BLOCKS; x++) {
            for (int z = 0; z < HOUSE_SIZE_IN_BLOCKS; z++) {
                world.getBlockAt(base.clone().add(x, 0, z)).setType(Material.OAK_PLANKS);
            }
        }

        // Walls + roof
        for (int x = 0; x < HOUSE_SIZE_IN_BLOCKS; x++) {
            for (int y = 1; y < HOUSE_SIZE_IN_BLOCKS - 1; y++) {
                for (int z = 0; z < HOUSE_SIZE_IN_BLOCKS; z++) {
                    boolean wall = (x == 0 || x == HOUSE_SIZE_IN_BLOCKS - 1 || z == 0 || z == HOUSE_SIZE_IN_BLOCKS - 1);
                    if (wall) {
                        world.getBlockAt(base.clone().add(x, y, z)).setType(Material.OAK_PLANKS);
                    } else {
                        // Clear inside space
                        world.getBlockAt(base.clone().add(x, y, z)).setType(Material.AIR);
                    }
                }
            }
        }

        // Roof
        for (int x = 0; x < HOUSE_SIZE_IN_BLOCKS; x++) {
            for (int z = 0; z < HOUSE_SIZE_IN_BLOCKS; z++) {
                world.getBlockAt(base.clone().add(x, HOUSE_SIZE_IN_BLOCKS - 1, z)).setType(Material.OAK_PLANKS);
            }
        }

        // Door (2 blocks high)
        Location doorBase = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2, 0, 0);
        Door door = (Door) Bukkit.createBlockData(Material.OAK_DOOR);
        door.setHalf(Door.Half.BOTTOM); door.setFacing(BlockFace.SOUTH);
        door.setOpen(false);
        doorBase.getBlock().setBlockData(door);
        Door doorTop = (Door) Bukkit.createBlockData(Material.OAK_DOOR);
        doorTop.setHalf(Door.Half.TOP); doorTop.setFacing(BlockFace.SOUTH);
        doorTop.setOpen(false);
        doorBase.getBlock().getRelative(BlockFace.UP).setBlockData(doorTop);

        // Torch inside
        world.getBlockAt(base.clone().add(1, HOUSE_SIZE_IN_BLOCKS - 2, 1)).setType(Material.TORCH);

        inv.removeItem(new ItemStack(Material.OAK_LOG, WOOD_NEEDED_FOR_HOUSE));
        this.hasHouse = true;

        // Move to a solid block *inside*
        Location inside = base.clone().add(2, 1, 2);
        npc.teleport(inside, PlayerTeleportEvent.TeleportCause.PLUGIN);

        return true;
    }

    private int getNumLogs() {
        return inv.all(Material.OAK_LOG).values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private Block findNearbyTree(int radius, boolean toBreak) {
        Location loc = npc.getEntity().getLocation();
        Block log = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 10; y++) { // check ground level ± some
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    Material mat = b.getType();
                    if (mat == Material.OAK_LOG) {
                        Block reachableTreeBlock = null;
                        if (toBreak) {
                            reachableTreeBlock = b;
                        } else {
                            for (int browseX = -CHOP_WOOD_RADIUS/2; browseX < CHOP_WOOD_RADIUS/2; browseX++) {
                                for (int browseZ = -CHOP_WOOD_RADIUS/2; browseZ < CHOP_WOOD_RADIUS/2; browseZ++) {
                                    if (npc.getNavigator().canNavigateTo(loc.clone().add(x + browseX, y, z + browseZ))) {
                                        reachableTreeBlock = loc.clone().add(x + browseX, y, z + browseZ).getBlock();
                                        break;
                                    }
                                }
                            }
                        }
                        if (reachableTreeBlock == null){
                            continue;
                        }
                        double dist = reachableTreeBlock.getLocation().distanceSquared(loc);
                        if (dist < bestDist) {
                            bestDist = dist;
                            log = reachableTreeBlock;
                        }
                    }
                }
            }
        }
        return log;
    }

    private void goToLocation(Location destination){
        // Try to acquire the lock (non-blocking)
        if (!locationSemaphore.tryAcquire()) {
            if (npc.getNavigator().getTargetAsLocation().distance(destination) <= CHOP_WOOD_RADIUS*CHOP_WOOD_RADIUS) {
                System.out.println("Already navigating to this location!");
                return;
            } else {
                System.out.println("Already on a trip! Cancelling this one");
                npc.getNavigator().cancelNavigation();

                if (!locationSemaphore.tryAcquire()) {
                    System.out.println("STILL CANT GET A TRIP");
                    return;
                }
            }
        }
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onComplete(NavigationCompleteEvent e) {
                if (e.getNPC().equals(npc)) {
                    locationSemaphore.release();
                    HandlerList.unregisterAll(this);
                }
            }

            @EventHandler
            public void onCancel(NavigationCancelEvent e) {
                if (e.getNPC().equals(npc)) {
                    System.out.println("NAVIGATION CANCELLED");
                    locationSemaphore.release();
                    HandlerList.unregisterAll(this);
                }
            }
        }, plugin);

        npc.getNavigator().setTarget(destination);

        final double stopSq = REACHED_DESTINATION_THRESHOLD * REACHED_DESTINATION_THRESHOLD;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (npc.getEntity().getLocation().distanceSquared(destination) <= stopSq) {
                    System.out.println("Reached destination.");
                    npc.getNavigator().cancelNavigation();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 5L);
    }
}
