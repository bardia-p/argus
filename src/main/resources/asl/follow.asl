!start.

+!start <-
  !followplayer("bardibloo").

+!loop <-
  .print("Hi from Jason!");
  say("Hello, Minecraft!");
  jump;
  .wait(1000);
  !loop.

+!followplayer(PlayerName) <-
    goto_player(PlayerName, 2);
    .wait(500);
    !followplayer(PlayerName).