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
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Random;

public class ArgusAgArch extends AgArch {
    private final Argus plugin;
    private final NPC npc;
    private final Inventory inv;
    private final Semaphore locationSemaphore;

    private boolean hasHouse;

    public static int WOOD_NEEDED_FOR_HOUSE = 30;
    public static int NAVIGATION_ERROR = 5;
    public static int CHOP_WOOD_RADIUS = 10;
    public static int ZOMBIE_KILL_RADIUS = 2;
    public static int TREE_BROWSE_RADIUS = 100;
    public static int HOUSE_SIZE_IN_BLOCKS = 4;
    public static int INVENTORY_SIZE = 27;
    public static int ACTION_DELAY = 250;

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
        result.add(Literal.parseLiteral("buildHouseWoodRequirement(" + WOOD_NEEDED_FOR_HOUSE + ")"));
        if (findNearbyTree(CHOP_WOOD_RADIUS, true) != null) {
            result.add(Literal.parseLiteral("canSeeTree"));
        }

        List<Zombie> zombies = findNearbyZombies(ZOMBIE_KILL_RADIUS);
        if (zombies.size() != 0) {
            result.add(Literal.parseLiteral("nearbyZombies(" + zombies.size() + ")"));
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
            case "attack_zombies" -> action.setResult(attackZombies());
            default -> System.out.println("Unknown action: " + actionName);
        }
        actionExecuted(action);
    }


    public void shutdown() {
        // Any cleanup if necessary
        System.out.println("I DIED");
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
        // Could not find a tree!
        if (log == null) {
            return false;
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
            return false;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                log.breakNaturally();
                for (Entity e : ent.getWorld().getNearbyEntities(ent.getLocation(), CHOP_WOOD_RADIUS,
                        CHOP_WOOD_RADIUS, CHOP_WOOD_RADIUS)) {
                    if (e instanceof Item item && item.getItemStack().getType() == Material.OAK_LOG) {
                        inv.addItem(item.getItemStack());
                        item.remove();
                        System.out.println("🪵 Picked up a log! Total log count is " + getNumLogs());
                    }
                }

                try {
                    Thread.sleep(ACTION_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }.runTask(plugin);

        return true;
    }

    public boolean buildHouse() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (getNumLogs() < WOOD_NEEDED_FOR_HOUSE) {
            System.out.println("NOT ENOUGH WOOD TO BUILD A HOUSE!");
            return false;
        }

        Location base = ent.getLocation();
        World world = base.getWorld();

        // Walls + roof
        for (int x = 0; x < HOUSE_SIZE_IN_BLOCKS; x++) {
            for (int y = 0; y < HOUSE_SIZE_IN_BLOCKS - 1; y++) {
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
        Location inside = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2, 0, 1);
        npc.teleport(inside, PlayerTeleportEvent.TeleportCause.PLUGIN);
        resetZombieTargets();

        return true;
    }

    public boolean attackZombies() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }
        List<Zombie> zombies = findNearbyZombies(ZOMBIE_KILL_RADIUS);

        if (zombies.size() == 0) {
            return false;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Zombie zombie = zombies.get(new Random().nextInt(zombies.size()));
                double damage = 1.0;
                zombie.damage(damage, ent);

                try {
                    Thread.sleep(ACTION_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }.runTask(plugin);

        return true;
    }

    private int getNumLogs() {
        return inv.all(Material.OAK_LOG).values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private Block findNearbyTree(int radius, boolean toBreak) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return null;
        }
        Location loc = ent.getLocation();
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

    private List<Zombie> findNearbyZombies(int radius) {
        ArrayList<Zombie> zombies = new ArrayList<>();
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return zombies;
        }
        for (Entity e : ent.getWorld().getNearbyEntities(ent.getLocation(), radius,
                radius, radius)) {
            if (e instanceof Zombie zombie) {
                zombies.add(zombie);
            }
        }

        return zombies;
    }

    private void goToLocation(Location destination){
        // Try to acquire the lock (non-blocking)
        if (!locationSemaphore.tryAcquire()) {
            if (npc.getNavigator().getTargetAsLocation().distance(destination) <= NAVIGATION_ERROR * NAVIGATION_ERROR) {
                System.out.println("Already navigating to this location!");
                return;
            } else {
                System.out.println("Replacing the current trip with a new one!");
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

        new BukkitRunnable() {
            @Override
            public void run() {
                Entity ent = npc.getEntity();
                // Agent has died
                if (!npc.isSpawned() || ent == null) {
                    cancel();
                    return;
                }
                if (ent.getLocation().equals(destination)) {
                    System.out.println("Reached destination.");
                    npc.getNavigator().cancelNavigation();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 5L);
    }

    private void resetZombieTargets(){
        for (Zombie zombie : npc.getEntity().getWorld().getEntitiesByClass(Zombie.class)) {
            Entity target = zombie.getTarget();
            if (target instanceof NPC) {
                if (target.equals(this.npc)) {
                    zombie.setTarget(null);
                    zombie.teleport(zombie.getLocation().add(0.01, 0, 0));
                }
            }
        }
    }
}
