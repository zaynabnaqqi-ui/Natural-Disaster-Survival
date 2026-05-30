The Great OOP Escape — Natural Disaster Survival
  A JavaFX-based survival game where you choose a world and try to survive natural disasters.
About:
  The Great OOP Escape is a multi-world, multi-game JavaFX desktop application featuring four fully playable game modes, six interactive disaster simulations, and a polished animated main menu all rendered using JavaFX Canvas with a custom game loop.
  Every background, world screen, and UI illustration was hand-designed in Canva and exported as PNG assets.

Worlds & Game Modes:
  Sugarcrest, Mushroomvile, Mineworld, pacman

Disaster Simulations:
  Flood: Rising water with animated waves and floating debris
  Meteor: Impacts and debris fall from above
  Earthquake: Ground shaking simulation
  Wildfire: Spreading fire simulation
  Tornado: Spinning vortex with wind speed HUD
  Volcanic Activity: Lava rivers, ash clouds, eruption intensity meter


Project Structure:
Natural-Disaster-Survival/
├── Assets/               # All PNG sprites, backgrounds, WAV audio
│   ├── mushroomvile/     # Mushroomvile sprites & sounds
│   ├── pmSprite/         # Pacman directional sprites
│   ├── pmSound/          # Pacman audio clips
│   └── sc/               # Sugarcrest assets
├── SourceCode/           # All .java source files
│   ├── Main.java
│   ├── GamePanel.java
│   ├── WorldScreen.java
│   ├── SimulationMenu.java
│   ├── SimUtils.java
│   ├── SugarcrestGame.java
│   ├── Mushroomvile.java
│   ├── Mineworld.java
│   ├── PacmanGame.java
│   ├── FloodSimulation.java
│   ├── VolcanoSimulation.java
│   ├── MeteorSimulation.java
│   ├── EarthquakeSimulation.java
│   ├── WildfireSimulation.java
│   └── TornadoSimulation.java
└── out/                  # Compiled output

Requirements to run this:
  Java 17+
  JavaFx 17+
  IDE: IntelliJ IDEA or VSCode


Author:
Zaynab Naqqi
BSCS-15B 
National University of Sciences and Technology (NUST)
School of Electrical Engineering and Computer Science
Course: CS-220 Object Oriented Programming
Instructor: Mr. Jaudat Mamoon
Lab Engineer: Mr. Moeed Ahmed
