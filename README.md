# Argus
Argus is a custom Minecraft mod for BDI agents to interact with each other in a multi-agent environment. In this 
environment, the agents act as NPCs with the goal of surviving the zombie attack and collecting as many points as 
possible. The agents can work together to fight the zombies and/or build houses to hide from them. 

## Installation
1. Get [Minecraft Java Edition](https://www.minecraft.net/en-us/store/minecraft-java-bedrock-edition-pc?tabs=%7B%22details%22%3A0%7D)
2. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/) (recommended!)
3. Install [IntelliJ Mincecraft plugin](https://plugins.jetbrains.com/plugin/8327-minecraft-development)
4. Get [Citizens plugin](https://github.com/CitizensDev/Citizens2/)
5. Get [CommandHelper plugin](https://www.spigotmc.org/resources/commandhelper.64681/)
6. Compile the project to create the `run` folder:
```
gradle runServer
```
7. Configure the server:
```
cp config/server.properties run/server.properties
cp config/eula.txt run/eula.txt
```
8. Copy the plugin jars you downloaded in steps 4 & 5 to `run/plugins`:
```
mv Citizens*.jar commandhelper*.jar run/plugins
```
9. Re-run the server to populate the plugin config folders:
```
gradle runServer
```
10. Configure the plugins:
```
cp config/Citizens/config.yml run/plugins/Citizens/config.yml
cp config/CommandHelper/main.ms run/plugins/CommandHelper/main.ms
```

## Running Argus

1. Launch Minecraft
2. Select `Multiplayer`
3. Create a server for Argus if one does not already exist:
   - Click `Add Server`
   - Put `Argus` for the `Server Name`
   - Put `localhost` for the `Server Address`
   - Click `Done`
4. Launch the server:
```
gradle runServer
```
(Argus should now show up as available in the list of servers)
6. Join the server!

## Using Argus
The games will by default run for 3-minutes. You can see what agent is doing in the chat log and inside your terminal.

To navigate the level use `WASD`. Use `space` for zooming out and `shift` for zooming in.

If you want to stop the game simply type `/stop` in the Minecraft chat window (you can launch the window by pressing `T`
or `/`).