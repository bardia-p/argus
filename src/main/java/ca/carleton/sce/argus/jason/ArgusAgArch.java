package ca.carleton.sce.argus.jason;

import ca.carleton.sce.argus.Argus;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSyntax.Literal;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.event.NavigationCancelEvent;
import net.citizensnpcs.api.ai.event.NavigationCompleteEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ArgusAgArch extends AgArch {
    // Infrastructure properties
    private final Argus plugin;
    private final NPC npc;
    private BukkitRunnable navigationTask;
    private boolean navigationListenersRegistered;

    // Agent properties
    private final Inventory inv;
    private List<Location> houses;
    private Location inHouse;
    private int score;

    // Constants
    public static int BROWSE_RADIUS = 25;
    // House building
    public static int WOOD_NEEDED_FOR_HOUSE = 30;
    public static int HOUSE_DISTANCE_OFFSET = 20;
    public static int HOUSE_SIZE_IN_BLOCKS = 4;
    // Wood chopping
    public static int INVENTORY_SIZE = 27;
    public static int CHOP_WOOD_RADIUS = 5;
    // Navigation
    public static int NAVIGATION_ERROR = 2;
    // Attacking
    public static int ATTACK_RADIUS = 4;
    public static int ESCAPE_RADIUS = 5;
    public static double HEALTH_REVIVE_AMOUNT_PER_TICK = 0.01;
    // Rewards
    public static int HOUSE_BUILD_REWARD = 200;
    public static int ZOMBIE_DAMAGE_REWARD = 10;

    public ArgusAgArch(Argus plugin, NPC npc) {
        this.plugin = plugin;
        this.npc = npc;
        this.inv = Bukkit.createInventory(null, INVENTORY_SIZE, this.getAgName() + "'s Inventory");
        this.navigationListenersRegistered = false;
        this.navigationTask = null;
        this.houses = new ArrayList<>();
        this.inHouse = null;
        this.score = 0;
        npc.data().set(NPC.Metadata.DEFAULT_PROTECTED, false);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!npc.isSpawned() || npc.getEntity() == null) {
                    cancel();
                    return;
                }

                Entity ent = npc.getEntity();
                // If in house, increase the entity's health.
                if (inHouse != null) {
                    LivingEntity entity = (LivingEntity) npc.getEntity();
                    double current = entity.getHealth();
                    double max = entity.getAttribute(Attribute.MAX_HEALTH).getValue();

                    double newHealth = Math.min(current + HEALTH_REVIVE_AMOUNT_PER_TICK * max, max);
                    entity.setHealth(newHealth);

                    // Reset any zombies targeting this NPC
                    for (Zombie zombie : ent.getWorld().getEntitiesByClass(Zombie.class)) {
                        if (zombie.getTarget().equals(npc)) {
                            zombie.setTarget(null);
                            zombie.teleport(zombie.getLocation().add(0.01, 0, 0));
                        }
                    }
                } else { // Make zombies come after the NPC
                    Location npcLoc = ent.getLocation();
                    double closestDistance = 1000;
                    Zombie closestZombie = null;
                    for (Zombie zombie : ent.getWorld().getEntitiesByClass(Zombie.class)) {
                        double distanceToZombie = zombie.getLocation().distanceSquared(npcLoc);
                        if (distanceToZombie < closestDistance) {
                            closestZombie = zombie;
                            closestDistance = distanceToZombie;
                        }

                        if (closestZombie != null) {
                            closestZombie.setTarget((LivingEntity) ent);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void shutdown() {
        // Any cleanup if necessary
        Bukkit.getLogger().info(getAgName() + " has stopped!!");
    }

    // Property functions

    public int getScore()  { return score; }

    // Jason functions

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
        result.add(Literal.parseLiteral("buildRequirement(house," + WOOD_NEEDED_FOR_HOUSE + ")"));
        if (findNearbyTree(CHOP_WOOD_RADIUS, true) != null) {
            result.add(Literal.parseLiteral("near(tree)"));
        }

        List<Zombie> zombies = findNearbyZombies(ATTACK_RADIUS);
        if (zombies.size() != 0) {
            result.add(Literal.parseLiteral("near(zombies," + zombies.size() + ")"));
        }

        List<NPC> players = findNearbyPlayers(ATTACK_RADIUS);
        if (players.size() != 0) {
            StringBuilder visiblePlayers = new StringBuilder();
            players.forEach(p -> visiblePlayers.append(p.getName()).append(","));
            result.add(Literal.parseLiteral("near(players,[" +
                    visiblePlayers.deleteCharAt(visiblePlayers.length()-1) + "])"));
        }

        // Check the player's houses
        for (Location houseLoc: houses) {
            if (findLivablePointInHouse(houseLoc) == null) {
                houses.remove(houseLoc);
            }
        }

        if (houses.size() > 0) {
            result.add(Literal.parseLiteral("houseCount(" + houses.size() + ")"));
        }

        if (this.inHouse != null) {
            result.add(Literal.parseLiteral("hiding"));
        }

        LivingEntity livingEnt = (LivingEntity) ent;
        double health = livingEnt.getHealth() / livingEnt.getAttribute(Attribute.MAX_HEALTH).getValue();
        result.add(Literal.parseLiteral("health(" + health + ")"));

        // Get the entity damage info
        EntityDamageEvent damageEvent = ent.getLastDamageCause();
        if (damageEvent != null && damageEvent.getDamageSource() != null) {
            Entity causingEntity = damageEvent.getDamageSource().getCausingEntity();
            if (causingEntity instanceof Zombie) {
                result.add(Literal.parseLiteral("damagedBy(zombie)"));
            } else { // Check to see if an NPC caused it.
                NPC causingNPC = CitizensAPI.getNPCRegistry().getNPC(causingEntity);
                if (causingNPC != null && causingNPC.getId() != npc.getId()) {
                    result.add(Literal.parseLiteral("damagedBy(" + causingNPC.getName() + ")"));
                }
            }
        }

        //Bukkit.getLogger().info("Agent " + getAgName() + " beliefs are: " + result);
        return result;
    }

    @Override
    public void act(ActionExec action) {
        String actionName = action.getActionTerm().getFunctor();
        //Bukkit.getLogger().info(npc.getName() + " performing action: " + actionName);
        switch (actionName) {
            case "say" -> {
                if (action.getActionTerm().getArity() == 1) {
                    String message = action.getActionTerm().getTerm(0).toString();
                    action.setResult(say(message));
                }
            }
            case "find" -> action.setResult(find(action.getActionTerm().getTerm(0).toString()));
            case "chop_wood" -> action.setResult(chopWood());
            case "escape" -> action.setResult(escape(action.getActionTerm().getTerm(0).toString()));
            case "attack" -> action.setResult(attack(action.getActionTerm().getTerm(0).toString()));
            case "build" -> action.setResult(build(action.getActionTerm().getTerm(0).toString()));
            case "enter_house" -> action.setResult(enterHouse());
            case "leave_house" -> action.setResult(leaveHouse());
            default -> Bukkit.getLogger().warning(npc.getName() + " has an unknown action: " + actionName);
        }
        actionExecuted(action);
    }

    // Abilities

    public boolean say(String message) {
        plugin.getServer().broadcastMessage("<" + getAgName() + "> " + message);
        return true;
    }

    public boolean find(String toFind) {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return false;
        }

        if (toFind.equals("tree")) {
            Block log = findNearbyTree(BROWSE_RADIUS, false);
            // Could not find a tree!
            if (log == null) {
                return false;
            }

            // Pathfind towards the player entity (will repath as they move)
            goToLocation(log.getLocation());
            return true;
        }

        return false;
    }

    public boolean chopWood() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return false;
        }

        Block log = findNearbyTree(CHOP_WOOD_RADIUS, true);

        // Could not find wood!
        if (log == null) {
            return false;
        }

        log.breakNaturally();
        inv.addItem(new ItemStack(Material.OAK_LOG, 1));

        return true;
    }

    public boolean escape(String from) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (from.equals("zombies")) {
            List<Zombie> zombies = findNearbyZombies(ATTACK_RADIUS);

            if (zombies.size() == 0) {
                return false;
            }

            int x_offset = getRandomLocationAtRadius(ESCAPE_RADIUS);
            int z_offset = getRandomLocationAtRadius(ESCAPE_RADIUS);
            Location escapeLoc = ent.getLocation().clone().add(x_offset, 0, z_offset);

            if (ent.getWorld().getBlockAt(escapeLoc).getType() == Material.AIR) {
                npc.teleport(escapeLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
                return true;
            }
        }

        return false;
    }

    public boolean attack(String entity) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (entity.equals("zombies")) {
            List<Zombie> zombies = findNearbyZombies(ATTACK_RADIUS);

            if (zombies.size() == 0) {
                return false;
            }

            Zombie zombie = zombies.get(new Random().nextInt(zombies.size()));
            double damage = 1.0;
            zombie.damage(damage, ent);
            this.score += ZOMBIE_DAMAGE_REWARD;
            return true;
        }

        return false;
    }

    public boolean build(String object) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (object.equals("house")) {
            if (getNumLogs() < WOOD_NEEDED_FOR_HOUSE) {
                Bukkit.getLogger().warning(getAgName() + " DOES NOT HAVE ENOUGH WOOD TO BUILD A HOUSE!");
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
                World world = ent.getWorld();

                // If there is entity within the house, find a new location
                for (Entity e : world.getEntities()) {
                    if (e instanceof Zombie || (e instanceof NPC n && !n.equals(npc))) {
                        if (e.getLocation().getX() >= base.getX() && e.getLocation().getX() <= base.getX() + HOUSE_SIZE_IN_BLOCKS
                                && e.getLocation().getZ() >= base.getZ() && e.getLocation().getZ() <= base.getZ() + HOUSE_SIZE_IN_BLOCKS) {
                            findHouseLocation = true;
                            break;
                        }
                    }
                }

                // Check if it conflicts with other houses.
                for (int x = 0; x < HOUSE_SIZE_IN_BLOCKS; x++) {
                    for (int z = 0; z < HOUSE_SIZE_IN_BLOCKS; z++) {
                        Block block = world.getBlockAt(base.clone().add(x, 0, z));
                        if (block.getType() == Material.OAK_PLANKS || block.getType() == Material.OAK_DOOR) {
                            findHouseLocation = true;
                            break;
                        }
                    }
                }
            }

            World world = base.getWorld();

            // Adding the walls and clearing the inside
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

            // Adding the roof
            for (int x = 0; x < HOUSE_SIZE_IN_BLOCKS; x++) {
                for (int z = 0; z < HOUSE_SIZE_IN_BLOCKS; z++) {
                    world.getBlockAt(base.clone().add(x, HOUSE_SIZE_IN_BLOCKS - 1, z)).setType(Material.OAK_PLANKS);
                }
            }

            // Adding the door
            Location doorBase = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2, 0, 0);
            Door door = (Door) Bukkit.createBlockData(Material.OAK_DOOR);
            door.setHalf(Door.Half.BOTTOM);
            door.setFacing(BlockFace.SOUTH);
            door.setOpen(false);
            doorBase.getBlock().setBlockData(door);
            Door doorTop = (Door) Bukkit.createBlockData(Material.OAK_DOOR);
            doorTop.setHalf(Door.Half.TOP);
            doorTop.setFacing(BlockFace.SOUTH);
            doorTop.setOpen(false);
            doorBase.getBlock().getRelative(BlockFace.UP).setBlockData(doorTop);

            // Putting a torch inside
            world.getBlockAt(base.clone().add(1, HOUSE_SIZE_IN_BLOCKS - 2, 1)).setType(Material.TORCH);

            inv.removeItem(new ItemStack(Material.OAK_LOG, WOOD_NEEDED_FOR_HOUSE));

            // Storing the house location
            Location inside = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2, 0, 1);
            houses.add(inside);
            this.score += HOUSE_BUILD_REWARD;

            return true;
        }

        return false;
    }

    public boolean enterHouse() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (houses.size() == 0) {
            Bukkit.getLogger().warning(getAgName() + " DOES NOT HAVE A HOUSE!");
            return false;
        }

        cancelNavigation();

        for (Location houseLoc: houses) {
            Location inside = findLivablePointInHouse(houseLoc);

            if (inside != null) {
                npc.teleport(inside, PlayerTeleportEvent.TeleportCause.PLUGIN);
                ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        Integer.MAX_VALUE, 1, false, false));
                this.inHouse = houseLoc;
                return true;
            }
        }

        return false;
    }

    public boolean leaveHouse() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (inHouse == null) {
            Bukkit.getLogger().warning(getAgName() + " IS NOT IN A HOUSE");
            return false;
        }

        cancelNavigation();

        for (int z_offset = 3; z_offset < BROWSE_RADIUS; z_offset++) {
            Location outside = inHouse.clone().subtract(0, 0, z_offset);
            if (ent.getWorld().getBlockAt(outside).getType() == Material.AIR) {
                // Make the NPC visible again
                ((LivingEntity) ent).removePotionEffect(PotionEffectType.INVISIBILITY);
                npc.teleport(outside, PlayerTeleportEvent.TeleportCause.PLUGIN);
                this.inHouse = null;
                return true;
            }
        }

        // Cannot leave the house
        return false;
    }

    // Helper functions

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

    private List<NPC> findNearbyPlayers(int radius) {
        ArrayList<NPC> players = new ArrayList<>();
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return players;
        }
        for (Entity e : ent.getWorld().getNearbyEntities(ent.getLocation(), radius, radius, radius)) {
            NPC nearbyNpc = CitizensAPI.getNPCRegistry().getNPC(e);

            if (nearbyNpc != null && nearbyNpc.getId() != npc.getId()) {
                players.add(nearbyNpc);
            }
        }

        return players;
    }

    private Location findLivablePointInHouse(Location houseLoc) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return null;
        }

        for (double x = -HOUSE_SIZE_IN_BLOCKS/2 + 1; x < HOUSE_SIZE_IN_BLOCKS/2; x++) {
            for (double z = 0; z < HOUSE_SIZE_IN_BLOCKS - 1; z++) {
                Location inside = houseLoc.clone().add(x, 0, z);
                if (ent.getWorld().getBlockAt(inside).getType() == Material.AIR) {
                    return inside;
                }
            }
        }

        return null;
    }

    private void goToLocation(Location destination){
        // If already heading roughly towards this location, ignore
        Location currentTarget = npc.getNavigator().getTargetAsLocation();
        if (currentTarget != null &&
                currentTarget.distanceSquared(destination) <= NAVIGATION_ERROR * NAVIGATION_ERROR) {
            //Bukkit.getLogger().warning("<NAVIGATION> " + getAgName() + " is already on this trip!");
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
                        //Bukkit.getLogger().warning("<NAVIGATION> " + getAgName() + " finished their trip!");
                    }
                }

                @EventHandler
                public void onCancel(NavigationCancelEvent e) {
                    if (e.getNPC().equals(npc)) {
                        //Bukkit.getLogger().warning("<NAVIGATION> " + getAgName() + " cancelled their trip!");
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

    private int getRandomLocationAtRadius(double radius) {
        return (int) Math.round((Math.random() + 1) * radius / 2 * (new Random().nextBoolean() ? 1 : -1));
    }
}
