package ca.carleton.sce.argus.cmd;

import ca.carleton.sce.argus.Argus;
import ca.carleton.sce.argus.jason.JasonService;
import ca.carleton.sce.argus.trait.JasonAgentTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnAgentCommand implements CommandExecutor, TabCompleter {
    private final Argus plugin;
    private final JasonService jasonService;

    public SpawnAgentCommand(Argus plugin, JasonService jasonService) {
        this.plugin = plugin;
        this.jasonService = jasonService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /spawnagent <agentName> <aslFile>");
            return true;
        }
        final String agentName = args[0];
        final String aslFile = args[1];

        // Locate the target block the player is looking at
        var block = player.getTargetBlockExact(15, FluidCollisionMode.NEVER);
        if (block == null || block.getType() == Material.AIR) {
            sender.sendMessage("Look at a solid block within 15 blocks.");
            return true;
        }
        Location spawnAt = block.getLocation().add(0.5, 1.01, 0.5);

        // Load ASL content from resources/asl/<aslFile>
        String aslPath = "asl/" + aslFile;
        String aslSource;
        try (InputStream in = plugin.getResource(aslPath)) {
            if (in == null) {
                sender.sendMessage("ASL not found in resources: " + aslPath);
                return true;
            }
            aslSource = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            sender.sendMessage("Failed to read " + aslPath + ": " + e.getMessage());
            return true;
        }

        // Create NPC via Citizens
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, agentName);
        npc.spawn(spawnAt);

        npc.data().setPersistent("argus_agent", true);

        // Attach trait with Jason wiring
        npc.getOrAddTrait(JasonAgentTrait.class);

        npc.setProtected(false);

        JasonAgentTrait trait = npc.getTraitNullable(JasonAgentTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "Failed to add JasonAgentTrait to NPC.");
            npc.despawn();
            npc.destroy();
            return true;
        }
        trait.initialize(agentName, aslFile, aslSource, jasonService);

        sender.sendMessage(ChatColor.GREEN + "Spawned agent NPC '" + agentName + "' using " + aslFile + ".");
        return true;
    }

    // Tab completion for <aslFile> from resources/asl/*
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.emptyList(); // agentName free-form
        }
        if (args.length == 2) {
            try {
                // List known ASL files from the plugin's JAR resources (best effort)
                // NOTE: Java doesn't easily list resources in a JAR; maintain an index txt or hardcode.
                // For convenience, we support a fixed shortlist here; replace with your own index.
                return List.of("hello.asl").stream().filter(f -> f.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            } catch (Exception ignored) {
            }
        }
        return Collections.emptyList();
    }
}