package ca.carleton.sce.argus.jason;

import ca.carleton.sce.argus.Argus;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSyntax.Literal;
import net.citizensnpcs.api.ai.event.NavigationCancelEvent;
import net.citizensnpcs.api.ai.event.NavigationCompleteEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArgusAgArch extends AgArch {
    private final Argus plugin;
    private final NPC npc;

    public ArgusAgArch(Argus plugin, NPC npc) {
        this.plugin = plugin;
        this.npc = npc;
    }

    @Override
    public List<Literal> perceive() {
        // TODO: Add perceptions
        npc.getEntity().getWorld().spawnParticle(Particle.GLOW, npc.getEntity().getLocation().add(0, 2, 0), 1, 0.2, 0.2, 0.2, 0.1);
        return new ArrayList<>();
    }

    @Override
    public void act(ActionExec action) {
        // TODO: Implement action handling
        String actionName = action.getActionTerm().getFunctor();
        plugin.getLogger().info("NPC " + npc.getName() + " performing action: " + actionName);
        switch (actionName) {
            case "say" -> {
                if (action.getActionTerm().getArity() == 1) {
                    String message = action.getActionTerm().getTerm(0).toString();
                    action.setResult(say(message));
                }
            }
            case "jump" -> action.setResult(jump());
            case "follow_nearest" -> {
                // follow_nearest(Radius, StopDist)
                double radius = Double.parseDouble(action.getActionTerm().getTerm(0).toString());
                double stop = Double.parseDouble(action.getActionTerm().getTerm(1).toString());


                if (!npc.isSpawned() || npc.getEntity() == null) {
                    action.setResult(false);
                    break;
                }
                var ent = npc.getEntity();
                var loc = ent.getLocation();

                var candidates = loc.getWorld().getNearbyEntities(loc, radius, radius, radius, e -> e instanceof Player);
                // If you're targeting plain Spigot, use loc.getWorld().getNearbyPlayers(loc, radius) instead.

                var target = candidates.stream().filter(p -> p.isValid() && !p.isDead() && p.getWorld().equals(loc.getWorld())).min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(loc))).orElse(null);

                if (target == null) {
                    action.setResult(false);
                    break;
                }

                // Pathfind towards the player entity (will repath as they move)
                npc.getNavigator().setTarget(target, false); // pathfinding on

                // Poll distance until within stop range, then finish the action
                final double stopSq = stop * stop;
                final var task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!npc.isSpawned() || target.isDead() || !target.getWorld().equals(npc.getEntity().getWorld())) {
                            cancel();
                            return;
                        }
                        double d2 = npc.getEntity().getLocation().distanceSquared(target.getLocation());
                        if (d2 <= stopSq) {
                            npc.getNavigator().cancelNavigation();
                            cancel();
                            action.setResult(true);
                            actionExecuted(action);
                        }
                    }
                };
                task.runTaskTimer(plugin, 1L, 5L); // check every 5 ticks

            }

            case "goto_player" -> {
                // goto_player(Name, StopDist/)
                String name = action.getActionTerm().getTerm(0).toString().replaceAll("^\"|\"$", "");
                say("Following " + name);
                double stop = Double.parseDouble(action.getActionTerm().getTerm(1).toString());
                var target = plugin.getServer().getPlayerExact(name);
                if (target == null || !target.isOnline()) {
                    action.setResult(false);
                    return;
                }
                if (!npc.isSpawned()) {
                    action.setResult(false);
                    break;
                }

                npc.getNavigator().setTarget(target, false); // pathfind to moving target

                // Finish when navigation completes...
                plugin.getServer().getPluginManager().registerEvents(new Listener() {
                    @EventHandler
                    public void onComplete(NavigationCompleteEvent e) {
                        if (e.getNPC().equals(npc)) {
                            plugin.getLogger().info("Reached " + name);
                            HandlerList.unregisterAll(this);
                        }
                    }

                    @EventHandler
                    public void onCancel(NavigationCancelEvent e) {
                        if (e.getNPC().equals(npc)) {
                            plugin.getLogger().info("Navigation to " + name + " was cancelled.");
                            HandlerList.unregisterAll(this);
                        }
                    }
                }, plugin);

                // ...or finish when within 'stop' distance (whichever happens first)
                final double stopSq = stop * stop;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!npc.isSpawned() || target.isDead() || !target.getWorld().equals(npc.getEntity().getWorld())) {
                            cancel();
                            return;
                        }
                        if (npc.getEntity().getLocation().distanceSquared(target.getLocation()) <= stopSq) {
                            npc.getNavigator().cancelNavigation();
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 1L, 5L);

                action.setResult(true);
                actionExecuted(action);
            }
            default ->
                    plugin.getLogger().warning("Unknown action: " + actionName);
        }
        actionExecuted(action);
    }


    public void shutdown() {
        // Any cleanup if necessary
    }

    public boolean say(String message) {
        plugin.getServer().broadcastMessage("<" + npc.getName() + "> " + message);
        return true;
    }

    public boolean jump() {
        if (!npc.getEntity().isOnGround()) return false;
        npc.getEntity().setVelocity(new Vector(0, 0.5, 0));
        return true;
    }
}
