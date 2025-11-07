@server_name = 'Argus';

# Echo server greeting
console(
    'CommandHelper successfully installed! To get started, visit https://methodscript.com, and visit the learning'
    . ' trail at the bottom, and particularly the Beginner\'s and Advanced Guides.'
);

# Event binding
bind('player_join', null, null, @event, @server_name,
    @player_name = 'bardibloo';
    @min = -25;
    @max = 25;
    @step = 5;
    @numZombies = 5;
    @zombieOffset = 0;

    # Game settings
    run('/time set night');
    run('/weather clear');
    run('/gamerule doDaylightCycle false');
    run('/gamerule doWeatherCycle false');
    run('/gamemode spectator');
    run('/gamerule randomTickSpeed 25');

    # Plant saplings
    for(@x = @min, @x <= @max, @x += 5,
        for(@z = @min, @z <= @max, @z += 5,
            run('/setblock ~'.@x.' ~ ~'.@z.' minecraft:oak_sapling');
        )
    )

    # Spawn zombies
    for(@i = 0, @i < @numZombies, @i += 1,
       @countX = ((@max - @min) / @step) + @zombieOffset;
       @countZ = ((@max - @min) / @step) + @zombieOffset;

       @randXIndex = rand(0, @countX - 1);
       @randZIndex = rand(0, @countZ - 1);

       @rx = @min + (@randXIndex * @step);
       @rz = @min + (@randZIndex * @step);

       run('/summon zombie ~'.@rx.' ~ ~'.@rz.' {NoAI:0b,PersistenceRequired:1b,Attributes:[{Name:"generic.follow_range",Base:40},{Name:"generic.movement_speed",Base:0.3}],CanPickUpLoot:0b,Silent:1b}');
    )
    run('/tp '.@player_name.' ~ ~5 ~ 0 90');

    run('/spawnagent cass hello.asl');
    run('/spawnagent eric hello.asl');
    run('/spawnagent bardia hello.asl');
    run('/spawnagent charissa hello.asl');
    run('/spawnagent daniel hello.asl');

    msg('Welcome to '.@server_name.'!');
)
