# Group D Agent – Sushi Go! (TAG Framework)

Our Group D agent is based on the Basic MCTS approach but implements its **own search tree (`GroupDTreeNode`)** for experimentation and extensions.

## How to Run
- Place the `groupD` folder inside `/src/players/`.
- Build and run TAG.
- In your experiment or launcher:
  ```java
  new players.groupD.GroupDMCTSPlayer();
