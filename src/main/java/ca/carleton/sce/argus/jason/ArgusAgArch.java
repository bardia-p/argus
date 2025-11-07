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
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ArgusAgArch extends AgArch {
    private final Argus plugin;
    private final NPC npc;
    private final Inventory inv;
    private BukkitRunnable navigationTask;
    private boolean navigationListenersRegistered;

    private List<Location> houses;
    private boolean inHouse;

    public static int WOOD_NEEDED_FOR_HOUSE = 30;
    public static int NAVIGATION_ERROR = 2;
    public static int CHOP_WOOD_RADIUS = 5;
    public static int ZOMBIE_KILL_RADIUS = 2;
    public static int TREE_BROWSE_RADIUS = 25;
    public static int ZOMBIE_ESCAPE_RADIUS = 5;
    public static int HOUSE_SIZE_IN_BLOCKS = 4;
    public static int INVENTORY_SIZE = 27;
    public static int HOUSE_DISTANCE_OFFSET = 10;

    public ArgusAgArch(Argus plugin, NPC npc) {
        this.plugin = plugin;
        this.npc = npc;
        this.inv = Bukkit.createInventory(null, INVENTORY_SIZE, this.getAgName() + " Inventory");
        this.navigationListenersRegistered = false;
        this.navigationTask = null;
        this.houses = new ArrayList<>();
        this.inHouse = false;
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

        if (houses.size() > 0) {
            result.add(Literal.parseLiteral("hasHouse"));
        }

        if (this.inHouse) {
            result.add(Literal.parseLiteral("inHouse"));
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
            case "escape_zombies" -> action.setResult(escapeZombies());
            case "attack_zombies" -> action.setResult(attackZombies());
            case "build_house" -> action.setResult(buildHouse());
            case "enter_house" -> action.setResult(enterHouse());
            default -> System.out.println("Unknown action: " + actionName);
        }
        actionExecuted(action);
    }


    public void shutdown() {
        // Any cleanup if necessary
        System.out.println(getAgName() + " DIED");
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

        log.breakNaturally();
        inv.addItem(new ItemStack(Material.OAK_LOG, 1));

        return true;
    }

    public boolean escapeZombies() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }
        List<Zombie> zombies = findNearbyZombies(ZOMBIE_KILL_RADIUS);

        if (zombies.size() == 0) {
            return false;
        }

        int x_offset = getRandomLocationAtRadius(ZOMBIE_ESCAPE_RADIUS);
        int z_offset = getRandomLocationAtRadius(ZOMBIE_ESCAPE_RADIUS);
        Location escapeLoc = ent.getLocation().clone().add(x_offset, 0, z_offset);

        if (ent.getWorld().getBlockAt(escapeLoc).getType() == Material.AIR)  {
            npc.teleport(escapeLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return true;
        }

        return false;
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

        Zombie zombie = zombies.get(new Random().nextInt(zombies.size()));
        double damage = 1.0;
        zombie.damage(damage, ent);

        return true;
    }

    public boolean buildHouse() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (getNumLogs() < WOOD_NEEDED_FOR_HOUSE) {
            System.out.println(getAgName() + " DOES NOT HAVE ENOUGH WOOD TO BUILD A HOUSE!");
            return false;
        }

        cancelNavigation();

        // Find a safe house location
        boolean findHouseLocation = true;
        Location base = ent.getLocation().clone();
        while (findHouseLocation) {
            findHouseLocation = false;
            int x_offset = getRandomLocationAtRadius(HOUSE_DISTANCE_OFFSET);
            int z_offset = getRandomLocationAtRadius(HOUSE_DISTANCE_OFFSET);
            base = ent.getLocation().clone().add(x_offset, 0, z_offset);

            // If there is entity within the house, find a new location
            for (Entity e : ent.getWorld().getEntities()) {
                if (e instanceof Zombie || (e instanceof NPC n && !n.equals(npc))) {
                    if (e.getLocation().getX() >= base.getX() && e.getLocation().getX() <= base.getX() + HOUSE_SIZE_IN_BLOCKS
                            && e.getLocation().getZ() >= base.getZ() && e.getLocation().getZ() <= base.getZ() + HOUSE_SIZE_IN_BLOCKS) {
                        findHouseLocation = true;
                        break;
                    }
                }
            }
        }

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

        // Store the house location
        Location inside = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2, 0, 1);
        houses.add(inside);

        return true;
    }

    public boolean enterHouse() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (houses.size() == 0) {
            System.out.println(getAgName() + " DOES NOT HAVE A HOUSE!");
            return false;
        }

        cancelNavigation();

        for (Location houseLoc: houses) {
            for (double x = -HOUSE_SIZE_IN_BLOCKS/2 + 1; x < HOUSE_SIZE_IN_BLOCKS/2; x++) {
                for (double z = 0; z < HOUSE_SIZE_IN_BLOCKS - 1; z++) {
                    Location inside = houseLoc.clone().add(x, 0, z);
                    if (ent.getWorld().getBlockAt(inside).getType() == Material.AIR) {
                        npc.teleport(inside, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        resetZombieTargets();
                        this.inHouse = true;
                        return true;
                    }
                }
            }
        }

        return false;
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
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= 10; y++) { // check ground level ± some
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
                                        reachableTreeBlock = loc.clone().add(x + browseX, 0, z + browseZ).getBlock();
                                        break;
                                    }
                                }
                            }
                        }
                        if (reachableTreeBlock != null){
                            return reachableTreeBlock;
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<Zombie> findNearbyZombies(int radius) {
        ArrayList<Zombie> zombies = new ArrayList<>();
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return zombies;
        }
        for (Entity e : ent.getWorld().getNearbyEntities(ent.getLocation(), radius, radius, radius)) {
            if (e instanceof Zombie zombie) {
                zombies.add(zombie);
            }
        }

        return zombies;
    }

    private void goToLocation(Location destination){
        // If already heading roughly towards this location, ignore
        Location currentTarget = npc.getNavigator().getTargetAsLocation();
        if (currentTarget != null &&
                currentTarget.distanceSquared(destination) <= NAVIGATION_ERROR * NAVIGATION_ERROR) {
            System.out.println(getAgName() + " is already on this trip!");
            return;
        }

        // Set new navigation target
        npc.getNavigator().setTarget(destination);

        // Register listeners once per NPC
        if (!navigationListenersRegistered) {
            navigationListenersRegistered = true;
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onComplete(NavigationCompleteEvent e) {
                    if (e.getNPC().equals(npc)) {
                        System.out.println(getAgName() + " finished their trip!");
                    }
                }

                @EventHandler
                public void onCancel(NavigationCancelEvent e) {
                    if (e.getNPC().equals(npc)) {
                        System.out.println(getAgName() + " cancelled their trip!");
                    }
                }
            }, plugin);
        }

        // Start only ONE repeating task
        if (navigationTask == null || navigationTask.isCancelled()) {
            navigationTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!npc.isSpawned() || npc.getEntity() == null) {
                        cancel();
                        return;
                    }

                    if (!npc.getNavigator().isNavigating()) {
                        cancel();
                        return;
                    }

                    if (npc.getEntity().getLocation().distanceSquared(
                            npc.getNavigator().getTargetAsLocation()) <= NAVIGATION_ERROR * NAVIGATION_ERROR) {
                        npc.getNavigator().cancelNavigation();
                        cancel();
                    }
                }
            };
            navigationTask.runTaskTimer(plugin, 0L, 20L);
        }
    }

    private void cancelNavigation() {
        npc.getNavigator().cancelNavigation();
        if (navigationTask != null) {
            navigationTask.cancel();
        }
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

    private int getRandomLocationAtRadius(double radius) {
        return (int) Math.round((Math.random() + 1) * radius / 2 * (new Random().nextBoolean() ? 1 : -1));
    }
}
