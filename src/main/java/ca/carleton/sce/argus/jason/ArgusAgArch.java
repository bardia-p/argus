package ca.carleton.sce.argus.jason;

import ca.carleton.sce.argus.Argus;
import jason.RevisionFailedException;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Agent;
import jason.asSyntax.Literal;

import jason.asSyntax.Term;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.event.NavigationCancelEvent;
import net.citizensnpcs.api.ai.event.NavigationCompleteEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;

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

import java.util.*;

public class ArgusAgArch extends AgArch {
    // Infrastructure properties
    private final Argus plugin;
    private final NPC npc;
    private BukkitRunnable navigationTask;
    private boolean navigationListenersRegistered;
    private static final Random RNG = new Random(System.currentTimeMillis());

    // Agent properties
    private final Inventory inv;
    private final List<Location> houses;
    private Location inHouse;
    private int score;
    private int attackPower;
    private String weapon;
    private int escapeRadius;
    private int simulatenousZombieCapability;

    // Constants
    public static int BROWSE_RADIUS = 50;
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
    public  static int DEFAULT_SIMULATENOUS_ZOMBIE_CAPABILITY = 1;
    public static int ATTACK_RADIUS = 5;
    public static int ESCAPE_RADIUS = 10;
    public static int DEFAULT_ATTACK_POWER = 1;
    public static int SWORD_ATTACK_POWER = 2;
    public static int AXE_ATTACK_POWER = 4;
    public static int TRIDENT_ATTACK_POWER = 6;
    public static  int ZOMBIE_BROWSE_RADIUS = 10;
    // Weapon building
    public static int WOOD_NEEDED_FOR_SWORD = 10;
    public static int WOOD_NEEDED_FOR_AXE = 20;
    public static int WOOD_NEEDED_FOR_TRIDENT = 30;
    // Health revival
    public static double HEALTH_REVIVE_AMOUNT_PER_TICK = 0.01;
    // Rewards
    public static int HOUSE_BUILD_REWARD = 500;
    public static int ZOMBIE_DAMAGE_REWARD = 25;
    public static int WOOD_DONATION_REWARD = 50;
    public static int PLAYER_DAMAGE_PENALTY = -10;

    public ArgusAgArch(Argus plugin, NPC npc) {
        this.plugin = plugin;
        this.npc = npc;
        this.inv = Bukkit.createInventory(null, INVENTORY_SIZE, this.getAgName() + "'s Inventory");
        this.navigationListenersRegistered = false;
        this.navigationTask = null;
        this.houses = new ArrayList<>();
        this.inHouse = null;
        this.score = 0;
        this.attackPower = DEFAULT_ATTACK_POWER;
        this.weapon = null;
        this.escapeRadius = ESCAPE_RADIUS;
        this.simulatenousZombieCapability = DEFAULT_SIMULATENOUS_ZOMBIE_CAPABILITY;
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
                    double max = Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue();

                    double newHealth = Math.min(current + HEALTH_REVIVE_AMOUNT_PER_TICK * max, max);
                    entity.setHealth(newHealth);

                    // Reset any zombies targeting this NPC
                    for (Zombie zombie : ent.getWorld().getEntitiesByClass(Zombie.class)) {
                        LivingEntity target = zombie.getTarget();
                        if (target != null && target.equals(npc)) {
                            zombie.setTarget(null);
                            zombie.teleport(zombie.getLocation().add(0.01, 0, 0));
                        }
                    }
                } else { // Make zombies come after the NPC
                    Location npcLoc = ent.getLocation();
                    double closestDistance = ZOMBIE_BROWSE_RADIUS * ZOMBIE_BROWSE_RADIUS;
                    Zombie closestZombie = null;
                    for (Zombie zombie : ent.getWorld().getEntitiesByClass(Zombie.class)) {
                        double distanceToZombie = zombie.getLocation().distanceSquared(npcLoc);
                        if (distanceToZombie <= closestDistance) {
                            closestZombie = zombie;
                            closestDistance = distanceToZombie;
                        }
                    }
                    if (closestZombie != null) {
                        closestZombie.setTarget((LivingEntity) ent);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
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

        // Constants
        result.add(Literal.parseLiteral("woodsChopped(" + getNumLogs() + ")"));
        result.add(Literal.parseLiteral("buildRequirement(house," + WOOD_NEEDED_FOR_HOUSE + ")"));
        result.add(Literal.parseLiteral("buildRequirement(sword," + WOOD_NEEDED_FOR_SWORD + ")"));
        result.add(Literal.parseLiteral("buildRequirement(axe," + WOOD_NEEDED_FOR_AXE + ")"));
        result.add(Literal.parseLiteral("buildRequirement(trident," + WOOD_NEEDED_FOR_TRIDENT + ")"));
        result.add(Literal.parseLiteral("zombieDefenceLimit(" + simulatenousZombieCapability + ")"));

        // Adding all available players
        List<NPC> allPlayers = new ArrayList<>();
        for (NPC p: CitizensAPI.getNPCRegistry()) {
            if (p.isSpawned() && p.getEntity() != null && !p.equals(npc)) {
                allPlayers.add(p);
            }
        }
        if (allPlayers.size() > 0) {
            Collections.shuffle(allPlayers);
            StringBuilder allPlayersStringList = new StringBuilder();
            allPlayers.forEach(p -> allPlayersStringList.append(p.getName()).append(","));
            result.add(Literal.parseLiteral("allPlayers([" +
                    allPlayersStringList.deleteCharAt(allPlayersStringList.length() - 1) + "])"));
        }

        // Entities nearby
        // Trees
        if (findNearbyTree(CHOP_WOOD_RADIUS, true) != null) {
            result.add(Literal.parseLiteral("near(tree)"));
        }

        // Zombies
        List<Zombie> zombies = findNearbyZombies(ATTACK_RADIUS);
        if (zombies.size() > 0) {
            result.add(Literal.parseLiteral("near(zombie," + zombies.size() + ")"));
        }

        // Players
        List<NPC> nearbyPlayers = findNearbyPlayers(ATTACK_RADIUS);
        if (nearbyPlayers.size() > 0) {
            Collections.shuffle(nearbyPlayers);
            StringBuilder nearbyPlayersStringList = new StringBuilder();
            nearbyPlayers.forEach(p -> nearbyPlayersStringList.append(p.getName()).append(","));
            result.add(Literal.parseLiteral("near(player,[" +
                    nearbyPlayersStringList.deleteCharAt(nearbyPlayersStringList.length()-1) + "])"));
        }

        // Check the player's houses
        houses.removeIf(houseLoc -> findLivablePointInHouse(houseLoc) == null);
        if (houses.size() > 0) {
            result.add(Literal.parseLiteral("houseCount(" + houses.size() + ")"));
        }

        if (this.inHouse != null) {
            result.add(Literal.parseLiteral("hiding"));
        }

        // Health and damage
        LivingEntity livingEnt = (LivingEntity) ent;
        double health = livingEnt.getHealth() / Objects.requireNonNull(livingEnt.getAttribute(Attribute.MAX_HEALTH)).getValue();
        result.add(Literal.parseLiteral("health(" + health + ")"));

        if (weapon != null) {
            result.add(Literal.parseLiteral("hasWeapon(" + weapon + ")"));
        }

        // Get the entity damage info
        EntityDamageEvent damageEvent = ent.getLastDamageCause();
        if (damageEvent != null && ((LivingEntity) ent).getLastDamage() != 0) {
            Entity causingEntity = damageEvent.getDamageSource().getCausingEntity();
            if (causingEntity instanceof Zombie) {
                result.add(Literal.parseLiteral("damagedBy(zombie)"));
            } else { // Check to see if an NPC caused it.
                NPC causingNPC = CitizensAPI.getNPCRegistry().getNPC(causingEntity);
                if (causingNPC != null && causingNPC.getId() != npc.getId()) {
                    result.add(Literal.parseLiteral("damagedBy(" + causingNPC.getName() + ")"));
                }
            }
            ((LivingEntity) ent).setLastDamage(0);
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
                String message = "";
                for (Term t : action.getActionTerm().getTerms()) {
                    message += t;
                }
                if (message != "") {
                    action.setResult(say(message.replace("\"", "")));
                }
            }
            case "find" -> action.setResult(find(action.getActionTerm().getTerm(0).toString()));
            case "chop_wood" -> action.setResult(chopWood());
            case "escape" -> action.setResult(escape());
            case "attack" -> action.setResult(attack(action.getActionTerm().getTerm(0).toString()));
            case "build" -> action.setResult(build(action.getActionTerm().getTerm(0).toString()));
            case "enter_house" -> action.setResult(enterHouse());
            case "leave_house" -> action.setResult(leaveHouse());
            case "donate_wood" -> action.setResult(donateWood(action.getActionTerm().getTerm(0).toString()));
            case "receive_wood" -> action.setResult(receiveWood(action.getActionTerm().getTerm(0).toString()));
            default -> Bukkit.getLogger().warning(npc.getName() + " has an unknown action: " + actionName);
        }
        actionExecuted(action);
    }

    @Override
    public void sendMsg(jason.asSemantics.Message m) {
        String receiver = m.getReceiver();
        JasonService.RuntimeHandle target = plugin.getRuntimeHandle(receiver);
        if (target != null && receiver != getAgName()) {
            Agent targetAgent = target.agent();

            // Convert message content into a literal
            Literal belief = Literal.parseLiteral("message(" + m.getIlForce() + ","  + m.getSender() + "," + m.getPropCont().toString() + ")");

            // Add as a belief to the target agent
            try {
                targetAgent.getTS().getAg().addBel(belief);
            } catch (RevisionFailedException e) {
                throw new RuntimeException(e);
            }
        }
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

        Location destinationLocation;

        if (toFind.equals("tree")) {
            Block log = findNearbyTree(BROWSE_RADIUS, false);
            // Could not find a tree!
            if (log == null) {
                return false;
            }
            destinationLocation = log.getLocation();
        } else if (toFind.equals("zombie")) {
            List<Zombie> zombies = findNearbyZombies(BROWSE_RADIUS);
            if (zombies.size() == 0) {
                return false;
            }
            Zombie zombie = zombies.get(new Random().nextInt(zombies.size()));
            destinationLocation = zombie.getLocation();
        } else {
            NPC player = null;
            for (NPC p: CitizensAPI.getNPCRegistry()) {
                if (p.isSpawned() && p.getEntity() != null && p.getName().equals(toFind)){
                    player = p;
                    break;
                }
            }
            if (player == null) {
                return false;
            }
            destinationLocation = player.getEntity().getLocation();
        }

        goToLocation(destinationLocation);
        return true;
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

    public boolean escape() {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        int x_offset = getRandomLocationAtRadius(this.escapeRadius);
        int z_offset = getRandomLocationAtRadius(this.escapeRadius);
        Location escapeLoc = ent.getLocation().clone().add(x_offset, 0, z_offset);

        if (npc.getNavigator().canNavigateTo(escapeLoc)) {
            npc.teleport(escapeLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return true;
        }

        return false;
    }

    public boolean attack(String entity) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        if (entity.equals("zombie")) {
            List<Zombie> zombies = findNearbyZombies(ATTACK_RADIUS);
            if (zombies.size() == 0) {
                return false;
            }
            for (int i = 0; i < simulatenousZombieCapability; i++) {
                Zombie zombie = zombies.get(RNG.nextInt(zombies.size()));
                zombie.damage(this.attackPower, ent);
                this.score += ZOMBIE_DAMAGE_REWARD * this.attackPower;
            }
        } else {
            List<NPC> players = findNearbyPlayers(ATTACK_RADIUS);
            NPC player = null;
            for (NPC p: players) {
                if (p.getName().equals(entity)){
                    player = p;
                    break;
                }
            }

            if (player == null) {
                return false;
            }

            this.score -= PLAYER_DAMAGE_PENALTY * this.attackPower;
            ((LivingEntity) player.getEntity()).damage(this.attackPower, ent);
        }


        return true;
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
            if (world == null) {
                return false;
            }

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
            Location doorBase = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2.0, 0, 0);
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
            Location inside = base.clone().add(HOUSE_SIZE_IN_BLOCKS / 2.0, 0, 1);
            houses.add(inside);
            this.score += HOUSE_BUILD_REWARD;

            return true;
        } else { // building a weapon
            Equipment trait = npc.getOrAddTrait(Equipment.class);
            ItemStack weapon = null;
            int requiredWood = 0;
            int newAttackPower = 1;
            switch(object) {
                case "sword":
                    requiredWood = WOOD_NEEDED_FOR_SWORD;
                    weapon = new ItemStack(Material.WOODEN_SWORD);
                    newAttackPower = SWORD_ATTACK_POWER;
                    escapeRadius = ESCAPE_RADIUS - SWORD_ATTACK_POWER;
                    simulatenousZombieCapability = DEFAULT_SIMULATENOUS_ZOMBIE_CAPABILITY + SWORD_ATTACK_POWER / 2;
                    break;
                case "axe":
                    requiredWood = WOOD_NEEDED_FOR_AXE;
                    weapon = new ItemStack(Material.WOODEN_AXE);
                    newAttackPower = AXE_ATTACK_POWER;
                    escapeRadius = ESCAPE_RADIUS - AXE_ATTACK_POWER;
                    simulatenousZombieCapability = DEFAULT_SIMULATENOUS_ZOMBIE_CAPABILITY + AXE_ATTACK_POWER / 2;
                    break;
                case "trident":
                    requiredWood = WOOD_NEEDED_FOR_TRIDENT;
                    weapon = new ItemStack(Material.TRIDENT);
                    newAttackPower = TRIDENT_ATTACK_POWER;
                    escapeRadius = ESCAPE_RADIUS - TRIDENT_ATTACK_POWER;
                    simulatenousZombieCapability = DEFAULT_SIMULATENOUS_ZOMBIE_CAPABILITY + TRIDENT_ATTACK_POWER / 2;
                    break;
            }

            if (weapon != null) {
                if (getNumLogs() < requiredWood) {
                    Bukkit.getLogger().warning(getAgName() + " DOES NOT HAVE ENOUGH WOOD TO BUILD " + object + "!");
                    return false;
                }

                // Remove old weapon
                trait.set(Equipment.EquipmentSlot.HAND, null);

                this.attackPower = newAttackPower;
                this.weapon = object;
                trait.set(Equipment.EquipmentSlot.HAND, weapon);
                inv.removeItem(new ItemStack(Material.OAK_LOG, requiredWood));

                return true;
            }
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

    public boolean donateWood(String numWoods) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        inv.removeItem(new ItemStack(Material.OAK_LOG, Integer.parseInt(numWoods)));
        this.score += WOOD_DONATION_REWARD;
        return true;
    }

    public boolean receiveWood(String numWoods) {
        Entity ent = npc.getEntity();
        if (!npc.isSpawned() || ent == null) {
            return false;
        }

        inv.addItem(new ItemStack(Material.OAK_LOG, Integer.parseInt(numWoods)));

        return true;
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

        for (double x = -HOUSE_SIZE_IN_BLOCKS/2.0 + 1; x < HOUSE_SIZE_IN_BLOCKS/2.0; x++) {
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
                    //if (e.getNPC().equals(npc)) {
                    //    Bukkit.getLogger().warning("<NAVIGATION> " + getAgName() + " finished their trip!");
                    //}
                }

                @EventHandler
                public void onCancel(NavigationCancelEvent e) {
                    //if (e.getNPC().equals(npc)) {
                    //    Bukkit.getLogger().warning("<NAVIGATION> " + getAgName() + " cancelled their trip!");
                    //}
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
        return (int) Math.round((Math.random() + 1) * radius / 2 * (RNG.nextBoolean() ? 1 : -1));
    }
}
